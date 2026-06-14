# Subplan 3 — Multimodal Útil en Flujo Real
**Fecha:** 2026-06-14 | **Rama:** `main` | **Depende de:** ninguno (independiente)

---

## Objetivo

Cerrar la brecha entre generar contenido multimodal y poder usarlo: artefactos vinculados a
sesión, preview embebido en los paneles existentes, y STT con idioma seleccionable.

---

## Tarea 3.1 — Guardar artefactos vinculados a Session

### Problema actual
`ImagePanel`, `TtsPanel` y `SttPanel` generan contenido pero no lo persisten ni lo asocian
a ninguna sesión. Al cambiar de sesión se pierde todo.

### Diseño

**Nuevo archivo: `core/model/Artifact.kt`**
```kotlin
@Serializable
enum class ArtifactType { IMAGE, AUDIO, TRANSCRIPT }

@Serializable
data class Artifact(
    val id: String = UUID.randomUUID().toString(),
    val type: ArtifactType,
    val sessionId: String,
    val filename: String,         // relativo al directorio de datos del plugin
    val mimeType: String,
    val createdAt: Long = System.currentTimeMillis(),
    val prompt: String? = null,   // prompt que generó el artefacto (imagen/audio)
)
```

**Session.kt:**
```kotlin
@Serializable
data class Session(
    ...
    val artifacts: List<Artifact> = emptyList(),
)
```

**SessionRepository.kt — persistencia de artefactos:**
- Artefactos binarios: guardar en `{pluginDataDir}/{sessionId}/artifacts/{artifact.id}.{ext}`
- Los metadatos (`Artifact`) ya se serializan dentro de `Session` (JSON).

**AiManagerService.kt:**
```kotlin
fun saveArtifact(sessionId: String, artifact: Artifact, data: ByteArray): Result<Artifact>
fun getArtifacts(sessionId: String): Result<List<Artifact>>
fun openArtifact(artifact: Artifact)  // abre el archivo con el visor del SO
```

`saveArtifact` implementación:
1. Obtiene `pluginDataDir` via `PathManager.getSystemPath() / "aimanager"`.
2. Escribe `data` al path correspondiente.
3. Añade `Artifact` a la sesión activa y guarda.

`openArtifact`: usa `Desktop.getDesktop().open(File(...))`.

### Archivos afectados

| Archivo | Cambio |
|---------|--------|
| `core/model/Artifact.kt` | **NUEVO** — modelo de artefacto |
| `core/model/Session.kt` | + `artifacts: List<Artifact> = emptyList()` |
| `core/session/SessionRepository.kt` | ya serializa Session → automático; + método `artifactPath(artifact)` |
| `services/AiManagerService.kt` | + `saveArtifact`, `getArtifacts`, `openArtifact` |

### Checkpoints

- **CP-24** `./gradlew compileKotlin` — PASS: 0 errores.
- **CP-25** `./gradlew test` — PASS: Session existente deserializa con `artifacts = emptyList()`.
- **CP-26** Manual: generar imagen → guardada en `{systemPath}/aimanager/{sessionId}/artifacts/`; `getArtifacts` devuelve 1 elemento.

---

## Tarea 3.2 — Preview embebido en tabs y export con anexos

### Problema actual
`ImagePanel` muestra la URL de la imagen generada pero no la renderiza.  
`TtsPanel` genera audio pero no lo reproduce.  
`SttPanel` muestra la transcripción en un JBTextArea pero sin opciones de edición/copia.

### Diseño

**ImagePanel.kt — preview real:**
```kotlin
// Componente central: JLabel con ImageIcon escalado
private val imageLabel = JLabel("No image yet", SwingConstants.CENTER)
private val saveButton = JButton("Save…").apply { isEnabled = false }
private val openButton = JButton("Open in viewer").apply { isEnabled = false }
```
Al recibir URL de imagen generada:
1. Descargar bytes en pooled thread (`OkHttpClient.get(url).bytes()`).
2. `SwingUtilities.invokeLater { imageLabel.icon = ImageIcon(scale(bytes, 512, 512)) }`.
3. Llamar `service.saveArtifact(...)` → habilitar `saveButton` y `openButton`.

Para imágenes base64 (algunos providers devuelven `data:image/png;base64,...`): decodificar directamente.

**TtsPanel.kt — reproducción de audio:**
```kotlin
private val playButton = JButton("▶ Play").apply { isEnabled = false }
private val saveButton = JButton("Save…").apply { isEnabled = false }
private var audioBytes: ByteArray? = null
```
Al recibir `ByteArray` de TTS:
1. `service.saveArtifact(...)`.
2. Habilitar `playButton`: usar `javax.sound.sampled.AudioSystem` para reproducir WAV/PCM,
   o `Desktop.open(tmpFile)` para formatos no estándar (mp3).
3. `saveButton`: `JFileChooser` para guardar el archivo.

**SttPanel.kt — transcript editable + copy:**
```kotlin
private val transcriptArea = JBTextArea(8, 60).apply { isEditable = true; lineWrap = true }
private val copyButton = JButton("Copy to clipboard")
private val useInChatButton = JButton("Send to chat")  // inyecta texto en ChatPanel.input
```

**ExportEngine.kt — anexos en export:**
```kotlin
fun exportLatex(session: Session, mode: ExportMode, outputPath: Path): Result<Path>
// Ahora copia artefactos IMAGE al mismo directorio que el .tex para \includegraphics
```

**LatexExporter.kt:**
En `render()`, al final del body, añadir sección de anexos si hay artefactos IMAGE:
```latex
\section*{Attachments}
\includegraphics[width=\linewidth]{artifact-xxx.png}
```

### Archivos afectados

| Archivo | Cambio |
|---------|--------|
| `toolWindow/ImagePanel.kt` | + `JLabel` con preview escalado, `saveButton`, `openButton`, descarga + `saveArtifact` |
| `toolWindow/TtsPanel.kt` | + `playButton` (AudioSystem), `saveButton`, `saveArtifact` |
| `toolWindow/SttPanel.kt` | transcript editable + `copyButton` + `useInChatButton` |
| `core/export/ExportEngine.kt` | copia artefactos IMAGE junto al .tex en export |
| `core/export/LatexExporter.kt` | + sección `\includegraphics` para artefactos IMAGE |

### Checkpoints

- **CP-27** `./gradlew compileKotlin` — PASS: 0 errores.
- **CP-28** UI: generar imagen → se muestra preview en ImagePanel (no solo URL).
- **CP-29** UI: generar audio TTS → "▶ Play" reproduce el audio.
- **CP-30** UI: transcribir audio → transcript en área editable; "Copy to clipboard" copia el texto.
- **CP-31** Export con sesión que tiene artefacto IMAGE → .tex incluye `\includegraphics`; el archivo .png está en el mismo directorio.

---

## Tarea 3.3 — STT con selector de idioma y limpieza básica

### Problema actual
`SttPanel` no tiene selector de idioma. El API de Whisper acepta el parámetro `language`
pero no se pasa.

### Diseño

**AiProvider.kt — firma extendida:**
```kotlin
suspend fun speechToText(modelId: String, audio: ByteArray, language: String? = null): Result<String> =
    Result.failure(UnsupportedOperationException(...))
```

**OpenAiApiClient.kt:**
Pasar `language` en el formulario multipart de `/v1/audio/transcriptions` si no es null.

**SttPanel.kt:**
```kotlin
private val languageCombo = ComboBox(arrayOf(
    "auto", "es", "en", "fr", "de", "it", "pt", "ja", "zh", "ru", "ar"
)).apply { selectedItem = "auto" }
```
Al llamar a `service.speechToText()`, pasar `language = languageCombo.selectedItem?.takeIf { it != "auto" } as String?`.

**Limpieza básica de transcripción:**
```kotlin
private fun cleanTranscript(raw: String): String = raw
    .trimEnd()
    .replace(Regex("""\s{2,}"""), " ")           // colapsar espacios múltiples
    .replace(Regex("""(\w)([.!?])(\w)"""), "$1$2 $3")  // asegurar espacio tras puntuación
```
Aplicar antes de mostrar en `transcriptArea`.

### Archivos afectados

| Archivo | Cambio |
|---------|--------|
| `provider/AiProvider.kt` | + `language: String? = null` en `speechToText` |
| `provider/openai/OpenAiApiClient.kt` | pasar `language` en multipart STT |
| `provider/onemin/OneMinApiClient.kt` | pasar `language` si soportado |
| `services/AiManagerService.kt` | `speechToText(...)` + `language` param |
| `toolWindow/SttPanel.kt` | + `languageCombo`, `cleanTranscript()` |

### Checkpoints

- **CP-32** `./gradlew compileKotlin` — PASS: 0 errores.
- **CP-33** UI: seleccionar idioma "es" → transcripción → el API recibe `language=es` (verificable en log de OkHttp en modo debug).
- **CP-34** Transcripción con ruido de espacios dobles → mostrada limpia.

---

## Orden de implementación recomendado

```
3.1 (Artifact model + persistencia)  →  3.2 (Preview + export)  →  3.3 (STT idioma)
```
