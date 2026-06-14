# Sesión 2026-06-14 — Subplanes de Mejora Vertical

## Progreso Alcanzado

### ✅ Sub02 — Settings y Seguridad (COMPLETADO)

**Tareas implementadas:**
1. **ProviderValidator.kt** — Validación proactiva de URL, API key, model ID
   - Regex para validar URLs (http/https)
   - Validación de longitud mínima de API keys
   - Campo requerido para Model ID

2. **ProviderTestCache.kt** — Cache en memoria de resultados de test
   - Almacena último resultado por provider
   - Formato: "OK (2026-06-14 HH:MM)" | "FAIL: mensaje"

3. **ProviderSettingsPanel.kt actualizado**
   - Botón "Test All" — batch test de todos los providers
   - Botón "Diagnose" — dialog con metadatos (sin exponer API key)
   - Validación en tiempo real con DocumentListener
   - Labels rojo/verde para estado de validación

**Ficheros:**
- `/src/main/kotlin/.../settings/ProviderValidator.kt` (NUEVO)
- `/src/main/kotlin/.../settings/ProviderTestCache.kt` (NUEVO)
- `/src/main/kotlin/.../settings/ui/ProviderSettingsPanel.kt` (ACTUALIZADO)

**Estado:** `./gradlew compileKotlin` ✅ PASS

---

### 🔄 Sub01 — Robustez de Proveedores (EN PROGRESO - 60% completado)

**Tareas implementadas:**
1. **AiProviderError.kt** — Sealed class con 5 subtipos
   - AuthError (401, 403)
   - QuotaError (429)
   - NetworkError (IOException)
   - InvalidModelError (404 + "model")
   - ApiError (genérico)

2. **RetryPolicy.kt** — Retry con backoff exponencial
   - `maxAttempts`, `initialDelayMs`, `backoffMultiplier`, `retryableStatuses`
   - `withRetry()` suspend helper que respeta AiProviderError permanentes
   - No reintenta Auth/InvalidModel errors

3. **OpenAiApiClient.kt actualizado**
   - Constructor acepta `retryPolicy`, `connectTimeoutMs`, `readTimeoutMs`
   - `companion object.createClient()` con OkHttpClient configurable
   - Métodos ahora son `suspend` y usan `withRetry()`
   - Mapeo de errores HTTP → AiProviderError

4. **OneMinApiClient.kt actualizado**
   - Similar a OpenAi: retry, timeouts, mapeo de errores, suspend
   - Mantiene soporte para chat, image, TTS, STT

5. **OpenAiCompatibleProvider.kt actualizado**
   - Pasa parámetros de retry/timeout al crear OpenAiApiClient

6. **ProviderConfig.kt actualizado**
   - Campos nuevos: `retryPolicy`, `connectTimeoutMs`, `readTimeoutMs`

7. **AiManagerSettingsState.kt actualizado**
   - `ProviderEntry` con campos: `maxRetries`, `connectTimeoutSec`, `readTimeoutSec`

**Estado:** 
- `./gradlew compileKotlin` ✅ PASS
- `./gradlew test` ⚠️ FALLOS (tests en progreso - firmas suspend causan NoSuchMethodError)

**Próximas tareas:**
- [ ] Actualizar tests para usar `runBlocking` o suspender
- [ ] Implementar fallback chain en AiManagerService
- [ ] Agregar UI en ProviderSettingsPanel para timeout/retry

---

## Arquitectura de Errores

```
AiProviderError (sealed)
├── AuthError          (no reintenta)
├── QuotaError         (reintenta con backoff)
├── NetworkError       (reintenta con backoff)
├── InvalidModelError  (no reintenta)
└── ApiError           (reintenta < 5xx)
```

Todos los métodos de provider devuelven ahora `Result<T>` con error mapping garantizado.

---

## Pendientes para próximas sesiones

### Sub00 — UX Chat y Sesiones (⏳ TODO)
- Streaming token a token (Flow<String>)
- Rename/Pin/Search de sesiones
- Retry + Edit+Resend de mensajes

### Sub03 — Multimodal Útil (⏳ TODO)
- Artifact model y persistencia
- Preview embebido (imagen/audio)
- STT con idioma seleccionable

### Sub04 — Export y Conocimiento (⏳ TODO)
- Metadatos en LaTeX (fecha, provider, tokens, coste)
- Export Markdown + JSON
- 4 plantillas de export

### Sub05 — Calidad y Release (⏳ TODO)
- E2E tests con MockWebServer
- CI matrix (2 versiones IDE)
- Telemetría local opcional

---

## Verificaciones completadas

✅ Compilación: `./gradlew compileKotlin` PASS  
✅ Estructura: Imports resueltos, sin conflictos  
✅ Api clients: OpenAi + OnMin con retry/timeout/error mapping  
✅ Settings: ProviderValidator + ProviderTestCache + UI enhancements  

⚠️ Tests: Requiere actualización de firmas (suspend)  
⚠️ Fallback chain: Estructura lista, implementación en AiManagerService pendiente  

---

*Plan de continuación: Actualizar tests y completar Sub01 fallback, luego proceder con Sub00 streaming.*

