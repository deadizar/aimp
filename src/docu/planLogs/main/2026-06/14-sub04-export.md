# Subplan 4 — Export y Conocimiento
**Fecha:** 2026-06-14 | **Rama:** `main` | **Depende de:** sub01 (Usage/TokenUsage), sub03 (Artifact)

---

## Objetivo

Tres mejoras de export que hacen los artefactos generados más útiles e interoperables:
metadatos en LaTeX, nuevos formatos (Markdown/JSON), y tres plantillas de export.

---

## Tarea 4.1 — LatexExporter con metadatos (fecha, provider, modelo, tokens, coste estimado)

### Problema actual
`LatexExporter.render()` genera un documento sin metadatos de la sesión.
No se sabe cuándo se generó, qué provider/modelo se usó, ni cuántos tokens costó.

### Diseño

**Nuevo data class: `core/export/ExportMetadata.kt`**
```kotlin
data class ExportMetadata(
    val exportedAt: Long = System.currentTimeMillis(),
    val providerName: String = "",
    val providerInstanceId: String = "",
    val modelId: String = "",
    val totalPromptTokens: Int = 0,
    val totalCompletionTokens: Int = 0,
    val estimatedCostUsd: Double? = null,  // null si no se puede calcular
)
```

**LatexExporter.render() — firma extendida:**
```kotlin
fun render(session: Session, mode: ExportMode, meta: ExportMetadata = ExportMetadata()): String
```

Nueva sección en el preámbulo LaTeX:
```latex
\usepackage{booktabs}
...
\begin{document}
\maketitle
\begin{table}[h]
\centering
\begin{tabular}{ll}
\toprule
Exported at & {meta.exportedAt formateado ISO-8601} \\
Provider    & {meta.providerName} ({meta.modelId}) \\
Tokens      & {meta.totalPromptTokens} prompt / {meta.totalCompletionTokens} completion \\
Est. cost   & {meta.estimatedCostUsd ?? "N/A"} USD \\
\bottomrule
\end{tabular}
\end{table}
```

**ExportEngine.kt:**
```kotlin
fun exportLatex(session: Session, mode: ExportMode, meta: ExportMetadata, outputPath: Path): Result<Path>
```
(parámetro `meta` nuevo; backward compat: overload sin `meta` con `ExportMetadata()`).

**ChatPanel.kt — pasar metadatos en el flujo de export:**
```kotlin
val meta = ExportMetadata(
    providerName = service.availableProviders()
        .firstOrNull { it.instanceId == selectedProvider() }?.name ?: "",
    providerInstanceId = selectedProvider(),
    modelId = selectedModel(),
    totalPromptTokens = session.messages.mapNotNull { it.usage?.promptTokens }.sum(),
    totalCompletionTokens = session.messages.mapNotNull { it.usage?.completionTokens }.sum(),
)
exportEngine.exportLatex(session, exportMode, meta, path)
```

**Coste estimado:**
Tabla hardcoded de precios aproximados por modelo conocido (GPT-4o, GPT-4o-mini, etc.).
```kotlin
private val COST_PER_1K_TOKENS: Map<String, Pair<Double, Double>> = mapOf(
    "gpt-4o"      to (0.0025 to 0.010),
    "gpt-4o-mini" to (0.00015 to 0.0006),
    // ...
)
fun estimateCost(modelId: String, promptTokens: Int, completionTokens: Int): Double? {
    val (promptRate, completionRate) = COST_PER_1K_TOKENS[modelId] ?: return null
    return (promptTokens * promptRate + completionTokens * completionRate) / 1000.0
}
```

### Archivos afectados

| Archivo | Cambio |
|---------|--------|
| `core/export/ExportMetadata.kt` | **NUEVO** — data class con campos de metadata |
| `core/export/LatexExporter.kt` | + tabla de metadatos en preámbulo, `estimateCost()` |
| `core/export/ExportEngine.kt` | + parámetro `meta` en `exportLatex` |
| `toolWindow/ChatPanel.kt` | construir y pasar `ExportMetadata` en `exportSession()` |

### Checkpoints

- **CP-35** `./gradlew compileKotlin` — PASS: 0 errores.
- **CP-36** Export sesión con tokens → archivo .tex contiene tabla con provider, modelo, tokens.
- **CP-37** Modelo "gpt-4o-mini" con 1000 prompt + 500 completion → coste estimado = ~$0.00045.

---

## Tarea 4.2 — Export Markdown y JSON

### Problema actual
Solo existe export LaTeX. No hay interoperabilidad con otras herramientas (Obsidian,
Notion, scripts de análisis).

### Diseño

**Nuevo archivo: `core/export/MarkdownExporter.kt`**
```kotlin
class MarkdownExporter {
    fun render(session: Session, mode: ExportMode, meta: ExportMetadata = ExportMetadata()): String {
        val sb = StringBuilder()

        // Frontmatter YAML
        sb.appendLine("---")
        sb.appendLine("title: \"${session.title}\"")
        sb.appendLine("exported_at: \"${Instant.ofEpochMilli(meta.exportedAt)}\"")
        sb.appendLine("provider: \"${meta.providerName}\"")
        sb.appendLine("model: \"${meta.modelId}\"")
        sb.appendLine("tokens: { prompt: ${meta.totalPromptTokens}, completion: ${meta.totalCompletionTokens} }")
        if (meta.estimatedCostUsd != null) sb.appendLine("cost_usd: ${meta.estimatedCostUsd}")
        sb.appendLine("---")
        sb.appendLine()

        val messages = session.messages.filter {
            mode == ExportMode.FULL_TRANSCRIPT || it.role == MessageRole.ASSISTANT
        }
        messages.forEach { msg ->
            sb.appendLine("## ${msg.role.name.lowercase().replaceFirstChar { it.uppercase() }}")
            sb.appendLine()
            sb.appendLine(msg.content)
            sb.appendLine()
        }
        return sb.toString()
    }
}
```

**Nuevo archivo: `core/export/JsonExporter.kt`**
```kotlin
class JsonExporter {
    private val json = Json { prettyPrint = true }

    fun render(session: Session, mode: ExportMode, meta: ExportMetadata = ExportMetadata()): String {
        val messages = session.messages.filter {
            mode == ExportMode.FULL_TRANSCRIPT || it.role == MessageRole.ASSISTANT
        }
        val export = ExportPayload(
            sessionId = session.id,
            title = session.title,
            exportedAt = meta.exportedAt,
            provider = meta.providerName,
            model = meta.modelId,
            tokens = TokenSummary(meta.totalPromptTokens, meta.totalCompletionTokens),
            estimatedCostUsd = meta.estimatedCostUsd,
            messages = messages.map { MessageExport(it.role.name, it.content, it.timestamp) },
        )
        return json.encodeToString(ExportPayload.serializer(), export)
    }
}
```

**ExportEngine.kt:**
```kotlin
fun exportMarkdown(session: Session, mode: ExportMode, meta: ExportMetadata, outputPath: Path): Result<Path>
fun exportJson(session: Session, mode: ExportMode, meta: ExportMetadata, outputPath: Path): Result<Path>
```

**ChatPanel.kt — selector de formato:**
En `exportSession()`, añadir un paso de selección de formato ("LaTeX", "Markdown", "JSON")
antes del selector de modo. El `JFileChooser` usa la extensión correcta según formato.

### Archivos afectados

| Archivo | Cambio |
|---------|--------|
| `core/export/MarkdownExporter.kt` | **NUEVO** |
| `core/export/JsonExporter.kt` | **NUEVO** |
| `core/export/ExportEngine.kt` | + `exportMarkdown`, `exportJson` |
| `toolWindow/ChatPanel.kt` | + selector de formato en flujo de export |

### Checkpoints

- **CP-38** `./gradlew compileKotlin` — PASS: 0 errores.
- **CP-39** Export Markdown → archivo con frontmatter YAML + secciones `## User` / `## Assistant`.
- **CP-40** Export JSON → JSON válido deserializable con `kotlinx.serialization`; campo `tokens` presente.

---

## Tarea 4.3 — Plantillas de export

### Problema actual
Solo existen dos modos: `FULL_TRANSCRIPT` y `ASSISTANT_ONLY`. Faltan "code only" y "full audit".

### Diseño

**ExportMode.kt — nuevos valores:**
```kotlin
enum class ExportMode {
    FULL_TRANSCRIPT,     // todos los mensajes
    ASSISTANT_ONLY,      // solo ASSISTANT
    CODE_ONLY,           // solo bloques de código (```) de todos los mensajes
    FULL_AUDIT,          // FULL_TRANSCRIPT + tabla de metadatos completa por mensaje
}
```

**Lógica CODE_ONLY:**
```kotlin
private fun extractCodeBlocks(content: String): String {
    val regex = Regex("""```[\w]*\n(.*?)```""", RegexOption.DOT_MATCHES_ALL)
    return regex.findAll(content).joinToString("\n\n") { it.groupValues[1].trim() }
}
```
Filtrar mensajes cuyo extracto no esté vacío.

**Lógica FULL_AUDIT:**
Incluir por cada mensaje: rol, timestamp, tokens (prompt/completion), content.
En LaTeX: tabla por mensaje; en Markdown: frontmatter por mensaje con metadatos.

**Actualización de exportadores:**
- `LatexExporter.render()` — manejar `CODE_ONLY` (envuelve en `\begin{verbatim}`) y `FULL_AUDIT`.
- `MarkdownExporter.render()` — manejar `CODE_ONLY` (solo bloques) y `FULL_AUDIT` (metadata por mensaje).
- `JsonExporter.render()` — `CODE_ONLY` filtra a bloques; `FULL_AUDIT` incluye `timestamp` y `usage` por mensaje.

**ChatPanel.kt:**
El selector de modo muestra las cuatro opciones.

### Archivos afectados

| Archivo | Cambio |
|---------|--------|
| `core/export/ExportMode.kt` (extraer de ExportEngine.kt) | + `CODE_ONLY`, `FULL_AUDIT` |
| `core/export/LatexExporter.kt` | manejar nuevos modos |
| `core/export/MarkdownExporter.kt` | manejar nuevos modos |
| `core/export/JsonExporter.kt` | manejar nuevos modos |
| `toolWindow/ChatPanel.kt` | + 2 opciones en selector de modo |

### Checkpoints

- **CP-41** `./gradlew compileKotlin` — PASS: 0 errores.
- **CP-42** Export CODE_ONLY desde sesión con bloques de código → archivo contiene solo el código, sin texto conversacional.
- **CP-43** Export FULL_AUDIT JSON → cada mensaje incluye `timestamp` y `usage`.

---

## Orden de implementación recomendado

```
4.1 (ExportMetadata)  →  4.2 (MD + JSON exporters)  →  4.3 (nuevos modos)
```
