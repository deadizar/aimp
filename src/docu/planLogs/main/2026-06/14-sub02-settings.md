# Subplan 2 — Settings y Seguridad
**Fecha:** 2026-06-14 | **Rama:** `main` | **Depende de:** ninguno (puede ir primero)

---

## Objetivo

Tres mejoras de usabilidad y seguridad en `ProviderSettingsPanel`: test en lote, validación
proactiva de campos, y botón de diagnóstico que no expone credenciales.

---

## Tarea 2.1 — Test de conexión en lote para todos los providers

### Problema actual
El botón "Test Connection" solo prueba el provider actualmente seleccionado en el form.
No hay forma de verificar todos a la vez.

### Diseño

**ProviderSettingsPanel.kt — botón "Test All":**
```kotlin
button("Test All") { testAllConnections() }
```

```kotlin
private fun testAllConnections() {
    val providers = (0 until model.size()).map { model.getElementAt(it) }
    val results = StringBuilder("Connection Test Results:\n\n")
    val registry = ProviderRegistry()

    // Corre en pooled thread para no bloquear EDT
    ApplicationManager.getApplication().executeOnPooledThread {
        providers.forEach { entry ->
            val config = AiManagerSettings.toProviderConfigs()
                .firstOrNull { it.instanceId == entry.instanceId }
            val line = if (config == null) {
                "⚠ ${entry.name}: not saved yet (apply first)"
            } else {
                val r = runBlocking { registry.create(config).getOrNull()?.verify() }
                if (r?.isSuccess == true && r.getOrThrow().ok)
                    "✓ ${entry.name}: ${r.getOrThrow().message}"
                else
                    "✗ ${entry.name}: ${r?.exceptionOrNull()?.message ?: "init failed"}"
            }
            results.appendLine(line)
        }
        SwingUtilities.invokeLater {
            Messages.showInfoMessage(panel, results.toString(), "Batch Connection Test")
        }
    }
}
```

### Archivos afectados

| Archivo | Cambio |
|---------|--------|
| `settings/ui/ProviderSettingsPanel.kt` | + botón "Test All" + método `testAllConnections()` |

### Checkpoints

- **CP-17** `./gradlew compileKotlin` — PASS: 0 errores.
- **CP-18** UI: con 2+ providers configurados → "Test All" → dialog con una línea por provider (✓/✗).

---

## Tarea 2.2 — Validación proactiva de URL / modelo / API key

### Problema actual
No hay validación hasta que se pulsa "Test Connection". El usuario puede guardar configs
inválidas sin ninguna señal visual.

### Diseño

**Nuevo archivo: `settings/ProviderValidator.kt`**
```kotlin
object ProviderValidator {
    private val URL_REGEX = Regex("""^https?://[^\s/$.?#].[^\s]*$""")

    data class ValidationResult(val ok: Boolean, val message: String)

    fun validateBaseUrl(url: String): ValidationResult {
        if (url.isBlank()) return ValidationResult(false, "Base URL is required")
        if (!URL_REGEX.matches(url)) return ValidationResult(false, "Invalid URL format")
        return ValidationResult(true, "")
    }

    fun validateApiKey(key: String): ValidationResult {
        if (key.isBlank()) return ValidationResult(false, "API key is required")
        if (key.length < 8) return ValidationResult(false, "API key too short")
        return ValidationResult(true, "")
    }

    fun validateModelId(modelId: String): ValidationResult {
        if (modelId.isBlank()) return ValidationResult(false, "Model ID is required")
        return ValidationResult(true, "")
    }
}
```

**ProviderSettingsPanel.kt:**
- Añadir `JLabel` de validación rojo/verde debajo de `baseUrlField`, `apiKeyField`, `defaultModelField`.
- `DocumentListener` en cada campo que llama a `ProviderValidator` y actualiza el label.
- El botón "Add"/"Update" llama también a `ProviderValidator` antes de proceder; si algún campo
  es inválido, muestra el primer error y aborta.

```kotlin
private fun installValidation(field: JTextField, label: JLabel, validate: (String) -> ProviderValidator.ValidationResult) {
    field.document.addDocumentListener(object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) = update()
        override fun removeUpdate(e: DocumentEvent) = update()
        override fun changedUpdate(e: DocumentEvent) = update()
        private fun update() {
            val r = validate(field.text)
            label.text = if (r.ok) "" else r.message
            label.foreground = if (r.ok) JBColor.GREEN else JBColor.RED
        }
    })
}
```

### Archivos afectados

| Archivo | Cambio |
|---------|--------|
| `settings/ProviderValidator.kt` | **NUEVO** — validación de URL, key, model |
| `settings/ui/ProviderSettingsPanel.kt` | + labels de validación + DocumentListeners |

### Checkpoints

- **CP-19** `./gradlew compileKotlin` — PASS: 0 errores.
- **CP-20** UI: escribir URL malformada → label rojo aparece inmediatamente. Corregir → label desaparece. Intentar "Add" con URL inválida → bloqueado con mensaje.

---

## Tarea 2.3 — Botón "Diagnóstico" de credenciales y endpoint

### Problema actual
No hay forma de inspeccionar el estado de un provider configurado sin revelar la API key
ni hacer una petición real.

### Diseño

**ProviderSettingsPanel.kt — botón "Diagnose":**
```kotlin
button("Diagnose") { diagnoseProvider() }
```

```kotlin
private fun diagnoseProvider() {
    val entry = providerList.selectedValue ?: run {
        Messages.showWarningDialog("Select a provider first", "Diagnose"); return
    }

    val keyRef = entry.apiKeyRef
    val keySet = AiManagerSettings.readApiKey(keyRef).isNotBlank()
    val lastTestCache = ProviderTestCache.getLastResult(entry.instanceId)  // simple in-memory map

    val report = buildString {
        appendLine("Provider:   ${entry.name} (${entry.instanceId})")
        appendLine("Provider ID: ${entry.providerId}")
        appendLine("Base URL:   ${entry.baseUrl}")
        appendLine("Default model: ${entry.defaultModel}")
        appendLine("Capabilities: ${entry.capabilitiesCsv}")
        appendLine("API key:    ${if (keySet) "SET (${keyRef})" else "NOT SET"}")
        appendLine("Last test:  ${lastTestCache ?: "never run"}")
    }

    Messages.showInfoMessage(panel, report, "Provider Diagnostic — ${entry.name}")
}
```

**Nuevo objeto: `settings/ProviderTestCache.kt`**
```kotlin
object ProviderTestCache {
    private val cache = mutableMapOf<String, String>()  // instanceId → "OK (2026-06-14 12:00)" | "FAIL: ..."

    fun store(instanceId: String, result: String) { cache[instanceId] = result }
    fun getLastResult(instanceId: String): String? = cache[instanceId]
}
```
`testConnection()` y `testAllConnections()` actualizan `ProviderTestCache` tras cada test.

### Archivos afectados

| Archivo | Cambio |
|---------|--------|
| `settings/ProviderTestCache.kt` | **NUEVO** — cache in-memory de últimos resultados de test |
| `settings/ui/ProviderSettingsPanel.kt` | + botón "Diagnose" + método `diagnoseProvider()`; `testConnection()` y `testAllConnections()` actualizan cache |

### Checkpoints

- **CP-21** `./gradlew compileKotlin` — PASS: 0 errores.
- **CP-22** UI: seleccionar provider → "Diagnose" → dialog muestra Base URL, "API key: SET (aimanager:xxx)" sin revelar el valor, y "Last test: never run".
- **CP-23** Después de "Test Connection" → "Diagnose" → "Last test: OK (…)" o "Last test: FAIL: …".

---

## Orden de implementación recomendado

```
2.2 (ProviderValidator)  →  2.3 (ProviderTestCache + Diagnose)  →  2.1 (Test All)
```
Razón: Validator puede reutilizarse en Test All; ProviderTestCache es base de Diagnose.
