# Subplan 0 — UX de Chat y Sesiones
**Fecha:** 2026-06-14 | **Rama:** `main` | **Depende de:** sub01 (AiProviderError)

---

## Objetivo

Tres mejoras ortogonales de UX que transforman el panel de chat de prototipo a herramienta usable:
streaming token a token, gestión de sesiones (rename/search/pin), e iteración rápida (Retry/Edit).

---

## Tarea 0.1 — Streaming token a token en ChatPanel

### Problema actual
`OpenAiApiClient.chat()` tiene `stream = false` hardcoded. El UI muestra la respuesta completa
de golpe tras un wait de latencia de red completa.

### Diseño

**Capa de provider — nuevo contrato de streaming:**
```kotlin
// AiProvider.kt — añadir método con default no-op
suspend fun chatStream(request: ChatRequest): Flow<String> =
    flow { emit(chat(request).getOrThrow().content) }
```

**OpenAiApiClient — implementación SSE:**
```kotlin
fun chatStream(request: ChatRequest): Flow<String> = callbackFlow {
    val payload = OpenAiChatRequest(model=..., stream=true, messages=...)
    val call = httpClient.newCall(buildPost("/v1/chat/completions", payload))
    call.enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            response.body?.source()?.use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data: ") && line != "data: [DONE]") {
                        val chunk = parseStreamChunk(line.removePrefix("data: "))
                        trySend(chunk)
                    }
                }
            }
            close()
        }
        override fun onFailure(call: Call, e: IOException) { close(e) }
    })
    awaitClose { call.cancel() }
}
```

**AiManagerService — nuevo método:**
```kotlin
fun sendMessageStreaming(
    providerInstanceId: String,
    modelId: String,
    text: String,
    onToken: (String) -> Unit,
    onComplete: (Result<SendMessageResult>) -> Unit,
)
```
Implementación: abre sesión/mensaje igual que `sendMessage`, luego lanza corrutina en
`ApplicationManager.getApplication().executeOnPooledThread` que consume el `Flow` emitiendo
`SwingUtilities.invokeLater { onToken(chunk) }` en cada token.

**ChatPanel — cambio de UI:**
- `sendMessage()` llama a `sendMessageStreaming`
- `onToken` hace `append(chunk)` en `transcript` (desde EDT)
- `onComplete` finaliza la sesión y actualiza `tokenLabel`

### Archivos afectados

| Archivo | Cambio |
|---------|--------|
| `provider/AiProvider.kt` | + `chatStream(): Flow<String>` (default no-op) |
| `provider/openai/OpenAiApiClient.kt` | + `chatStream()` con SSE; extrae `buildPost()` helper |
| `provider/openai/OpenAiCompatibleProvider.kt` | override `chatStream` usando `OpenAiApiClient.chatStream` |
| `provider/onemin/OneMinApiClient.kt` | + `chatStream()` si el provider lo soporta, o default |
| `services/AiManagerService.kt` | + `sendMessageStreaming(...)` |
| `toolWindow/ChatPanel.kt` | `sendMessage()` → `sendMessageStreaming`; `transcript: JBTextArea` → streaming-safe append |

### Checkpoints

- **CP-00** `./gradlew compileKotlin` — PASS: 0 errores. FAIL: compilación rota.
- **CP-01** Abrir Run Plugin → enviar mensaje → respuesta aparece progresivamente. FAIL: texto aparece de golpe o no aparece.

---

## Tarea 0.2 — Rename / Búsqueda / Pin en HistoryPanel

### Problema actual
`HistoryPanel` es una lista pasiva sin ninguna operación sobre sesiones.

### Diseño

**Session.kt — campos nuevos:**
```kotlin
@Serializable
data class Session(
    val id: String,
    val title: String,
    val pinned: Boolean = false,           // nuevo
    val agentId: String? = null,
    val messages: List<Message> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)
```

**SessionManager.kt — nuevas operaciones:**
```kotlin
fun renameSession(sessionId: String, newTitle: String): Result<Session>
fun togglePin(sessionId: String): Result<Session>
fun deleteSession(sessionId: String): Result<Unit>
fun searchSessions(query: String): Result<List<Session>>
```

**HistoryPanel.kt — rediseño:**
- Añadir `JTextField` de búsqueda en `NORTH` con `DocumentListener` que filtra `model`
- `JBList` con renderer personalizado: icono de pin (📌) si `pinned`, título, fecha formateada
- Menú contextual (right-click `MouseAdapter`) con: Rename (dialog `Messages.showInputDialog`), Pin/Unpin, Delete (confirm)
- Ordenar: pinned first, luego por `updatedAt` desc

**AiManagerService.kt:**
Exponer los cuatro métodos nuevos de `SessionManager`.

### Archivos afectados

| Archivo | Cambio |
|---------|--------|
| `core/model/Session.kt` | + `pinned: Boolean = false` |
| `core/session/SessionManager.kt` | + rename, togglePin, delete, search |
| `services/AiManagerService.kt` | + exponer nuevas ops de sesión |
| `toolWindow/HistoryPanel.kt` | + search field, context menu, custom renderer, pin-first sort |

### Checkpoints

- **CP-02** `./gradlew compileKotlin` — PASS: 0 errores.
- **CP-03** `./gradlew test` — PASS: tests existentes pasan (Session sigue siendo deserializable con campo nuevo por defecto).
- **CP-04** UI manual: right-click → Rename → nuevo título aparece en lista. Pin → icono visible y sesión sube al top.

---

## Tarea 0.3 — Retry y Edit+Resend por mensaje

### Problema actual
No hay forma de reintentar un mensaje fallido ni editar y reenviar sin reiniciar la sesión.

### Diseño

**Message.kt — ID estable:**
```kotlin
@Serializable
data class Message(
    val id: String = UUID.randomUUID().toString(),  // nuevo
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val usage: TokenUsage? = null,
)
```

**SessionManager.kt — truncar historial:**
```kotlin
fun truncateAfterMessage(session: Session, messageId: String): Result<Session>
// Devuelve nueva sesión con messages hasta el mensaje (exclusive) con ese id.
```

**AiManagerService.kt:**
```kotlin
fun retryLastUserMessage(): Result<SendMessageResult>
fun editAndResend(upToMessageId: String, newText: String): Result<SendMessageResult>
```
- `retryLastUserMessage`: trunca al último USER message, reenvía.
- `editAndResend`: trunca antes del mensaje indicado, envía `newText` como nuevo USER.

**ChatPanel.kt — renderer de mensajes:**
Reemplazar el `JBTextArea` monolítico por un `JPanel` con `BoxLayout.Y_AXIS` donde cada mensaje
es un `MessageRow` (inner class): label de rol, texto en `JBTextArea(isEditable=false)`, botones
`[Retry]` (solo en último ASSISTANT) y `[Edit]` (solo en USER messages).

> **Nota de implementación:** El panel de mensajes es el cambio más invasivo del subplan.
> Usar `JScrollPane` con `scrollRectToVisible` para auto-scroll al último mensaje.

### Archivos afectados

| Archivo | Cambio |
|---------|--------|
| `core/model/Message.kt` | + `id: String = UUID.randomUUID().toString()` |
| `core/session/SessionManager.kt` | + `truncateAfterMessage` |
| `services/AiManagerService.kt` | + `retryLastUserMessage`, `editAndResend` |
| `toolWindow/ChatPanel.kt` | `JBTextArea` → panel de `MessageRow`s con botones Retry/Edit |

### Checkpoints

- **CP-05** `./gradlew compileKotlin` — PASS: 0 errores.
- **CP-06** `./gradlew test` — PASS: Message sigue siendo deserializable (id tiene default).
- **CP-07** UI manual: enviar mensaje → botón Edit → editar → reenvío va con texto nuevo; historial truncado correctamente.

---

## Orden de implementación recomendado

```
0.2 (Session model + HistoryPanel)  →  0.3 (Message model + retry)  →  0.1 (Streaming)
```
Razón: 0.2 y 0.3 modifican modelos serializados — hacer primero evita doble migración.
El streaming (0.1) es independiente de los modelos y puede ir último.
