# Subplan 1 — Robustez de Proveedores
**Fecha:** 2026-06-14 | **Rama:** `main` | **Depende de:** sub02 (ProviderValidator, opcional)

---

## Objetivo

Hacer que el sistema de providers sea resiliente ante fallos de red, errores de API y
saturación de cuota. Tres capas complementarias: retry con backoff, errores tipados, y
fallback automático de provider.

---

## Tarea 1.1 — Timeouts / Retries con backoff exponencial

### Problema actual
`OpenAiApiClient.defaultClient()` tiene timeouts fijos (connect=10s, read=60s, write=30s)
y no hace ningún retry. Un error transitorio de red mata la petición permanentemente.

### Diseño

**Nuevo archivo: `provider/RetryPolicy.kt`**
```kotlin
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 500L,
    val maxDelayMs: Long = 8_000L,
    val backoffMultiplier: Double = 2.0,
    val retryableStatuses: Set<Int> = setOf(429, 500, 502, 503, 504),
)

// Helper de extensión
suspend fun <T> withRetry(policy: RetryPolicy, block: suspend () -> Result<T>): Result<T> {
    var delayMs = policy.initialDelayMs
    repeat(policy.maxAttempts - 1) { attempt ->
        val result = block()
        if (result.isSuccess) return result
        val ex = result.exceptionOrNull()
        if (ex is AiProviderError.AuthError || ex is AiProviderError.InvalidModelError) return result
        delay(delayMs.coerceAtMost(policy.maxDelayMs))
        delayMs = (delayMs * policy.backoffMultiplier).toLong()
    }
    return block()
}
```

**ProviderConfig.kt:**
```kotlin
data class ProviderConfig(
    ...
    val retryPolicy: RetryPolicy = RetryPolicy(),
    val connectTimeoutMs: Long = 10_000L,
    val readTimeoutMs: Long = 60_000L,
)
```

**OpenAiApiClient.kt:**
- `defaultClient()` usa `connectTimeoutMs`/`readTimeoutMs` de config.
- `chat()` y `chatStream()` envueltos en `withRetry(config.retryPolicy) { ... }`.

**AiManagerSettingsState.ProviderEntry:**
```kotlin
val maxRetries: Int = 3
val connectTimeoutSec: Int = 10
val readTimeoutSec: Int = 60
```

**ProviderSettingsPanel.kt:**
Añadir tres campos numéricos: Max retries, Connect timeout (s), Read timeout (s).

### Archivos afectados

| Archivo | Cambio |
|---------|--------|
| `provider/RetryPolicy.kt` | **NUEVO** — data class + `withRetry` suspend helper |
| `provider/ProviderConfig.kt` | + `retryPolicy`, `connectTimeoutMs`, `readTimeoutMs` |
| `provider/openai/OpenAiApiClient.kt` | usar timeouts de config; envolver calls en `withRetry` |
| `settings/AiManagerSettingsState.kt` | + `maxRetries`, `connectTimeoutSec`, `readTimeoutSec` en `ProviderEntry` |
| `settings/ui/ProviderSettingsPanel.kt` | + campos numéricos para retry y timeouts |

### Checkpoints

- **CP-08** `./gradlew compileKotlin` — PASS: 0 errores.
- **CP-09** `./gradlew test` — PASS: `ProviderEntry` sigue deserializando con campos nuevos por defecto.
- **CP-10** Simular timeout: apuntar baseUrl a `http://localhost:1` → error en ≤ connect timeout, no cuelga indefinidamente.

---

## Tarea 1.2 — Errores normalizados por tipo

### Problema actual
Los errores llegan como `Exception("HTTP 401: ...")` en texto plano. El UI muestra el raw
string al usuario. No hay diferenciación de tipo de error.

### Diseño

**Nuevo archivo: `provider/AiProviderError.kt`**
```kotlin
sealed class AiProviderError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    // API key inválida o expirada (HTTP 401, 403)
    class AuthError(message: String, cause: Throwable? = null) : AiProviderError(message, cause)
    // Cuota agotada o rate limit (HTTP 429)
    class QuotaError(message: String, cause: Throwable? = null) : AiProviderError(message, cause)
    // Error de red o timeout
    class NetworkError(message: String, cause: Throwable? = null) : AiProviderError(message, cause)
    // Modelo no disponible en el provider (HTTP 404 + body contiene "model")
    class InvalidModelError(message: String, cause: Throwable? = null) : AiProviderError(message, cause)
    // Cualquier otro error HTTP
    class ApiError(val statusCode: Int, message: String, cause: Throwable? = null) : AiProviderError(message, cause)
}
```

**OpenAiApiClient.kt — mapeo de errores:**
```kotlin
private fun mapHttpError(code: Int, body: String): AiProviderError = when (code) {
    401, 403 -> AiProviderError.AuthError("Authentication failed (HTTP $code)")
    429      -> AiProviderError.QuotaError("Rate limit or quota exceeded (HTTP 429)")
    404      -> if ("model" in body.lowercase())
                    AiProviderError.InvalidModelError("Model not found: $body")
                else AiProviderError.ApiError(404, body)
    else     -> AiProviderError.ApiError(code, "HTTP $code: $body")
}
```
`IOException` → `AiProviderError.NetworkError`.

**ChatPanel.kt — mensajes amigables:**
```kotlin
private fun errorMessage(ex: Throwable): String = when (ex) {
    is AiProviderError.AuthError         -> "Authentication failed. Check your API key in Settings."
    is AiProviderError.QuotaError        -> "Quota exceeded. Try again later or switch provider."
    is AiProviderError.InvalidModelError -> "Model not found. Update the model ID in Settings."
    is AiProviderError.NetworkError      -> "Network error. Check your connection."
    else                                  -> ex.message ?: "Unknown error"
}
```

**RetryPolicy.withRetry** — no reintentar `AuthError` ni `InvalidModelError` (son permanentes).

### Archivos afectados

| Archivo | Cambio |
|---------|--------|
| `provider/AiProviderError.kt` | **NUEVO** — sealed class con 5 subtipos |
| `provider/openai/OpenAiApiClient.kt` | `executeGet`/`executePost` → `mapHttpError`; IOException → NetworkError |
| `provider/RetryPolicy.kt` | `withRetry` no reintenta Auth/InvalidModel |
| `toolWindow/ChatPanel.kt` | `errorMessage(ex)` con mensajes por tipo |

### Checkpoints

- **CP-11** `./gradlew compileKotlin` — PASS: 0 errores.
- **CP-12** Configurar API key inválida → send message → UI muestra "Authentication failed. Check your API key…" (no stack trace raw).
- **CP-13** Configurar modelo inválido → send message → UI muestra "Model not found…".

---

## Tarea 1.3 — Fallback automático de modelo/proveedor

### Problema actual
Si el provider activo falla, la sesión queda bloqueada hasta que el usuario cambia manualmente.

### Diseño

**AiManagerSettingsState:**
```kotlin
var fallbackProviderInstanceIds: List<String> = emptyList()
```

**AiManagerSettings.kt:**
```kotlin
fun getFallbackChain(): List<String>  // [activeId, ...fallbacks]
fun setFallbackChain(ids: List<String>)
```

**AiManagerService.sendMessage / sendMessageStreaming:**
```kotlin
val chain = AiManagerSettings.getFallbackChain()
var lastError: Throwable? = null
for (instanceId in chain) {
    val result = trySendWith(instanceId, modelId, text)
    if (result.isSuccess) return result
    lastError = result.exceptionOrNull()
    if (lastError is AiProviderError.AuthError) break  // auth errors don't improve with fallback
}
return Result.failure(lastError ?: error("No providers available"))
```

**ProviderSettingsPanel.kt:**
Añadir sección "Fallback chain" con lista ordenable (up/down buttons) de providers disponibles.

### Archivos afectados

| Archivo | Cambio |
|---------|--------|
| `settings/AiManagerSettingsState.kt` | + `fallbackProviderInstanceIds: List<String>` |
| `settings/AiManagerSettings.kt` | + `getFallbackChain`, `setFallbackChain` |
| `services/AiManagerService.kt` | `sendMessage` / `sendMessageStreaming` con fallback loop |
| `settings/ui/ProviderSettingsPanel.kt` | + sección Fallback chain con lista ordenable |

### Checkpoints

- **CP-14** `./gradlew compileKotlin` — PASS: 0 errores.
- **CP-15** Configurar provider primario con URL inválida + fallback válido → send message → respuesta del fallback; notificación en UI "Using fallback: {name}".
- **CP-16** `./gradlew test` — PASS: tests existentes no rotos.

---

## Orden de implementación recomendado

```
1.2 (AiProviderError)  →  1.1 (RetryPolicy usa AiProviderError)  →  1.3 (Fallback)
```
