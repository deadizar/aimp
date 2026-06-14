# Subplan 5 — Calidad y Release
**Fecha:** 2026-06-14 | **Rama:** `main` | **Depende de:** sub00, sub01, sub03, sub04 (superficie final)

---

## Objetivo

Tres palancas para hacer el plugin production-ready: tests E2E con MockWebServer,
matrix CI de compatibilidad IDE, y telemetría local opcional para guiar mejoras futuras.

---

## Tarea 5.1 — Tests E2E con MockWebServer

### Contexto
`mockwebserver:4.12.0` ya está en `build.gradle.kts`. Solo faltan los tests.

### Diseño — suite de tests

**`src/test/kotlin/.../e2e/SessionFlowTest.kt`**
```kotlin
class SessionFlowTest : BasePlatformTestCase() {
    private lateinit var server: MockWebServer
    private lateinit var service: AiManagerService

    override fun setUp() {
        super.setUp()
        server = MockWebServer()
        server.start()
        // Configurar provider con baseUrl = server.url("/").toString()
    }

    override fun tearDown() {
        server.shutdown()
        super.tearDown()
    }

    fun `test full session flow - create, send, save`() {
        server.enqueue(MockResponse()
            .setBody("""{"model":"gpt-4o","choices":[{"message":{"role":"assistant","content":"Hello!"}}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}""")
            .addHeader("Content-Type", "application/json"))

        val result = service.sendMessage(PROVIDER_INSTANCE_ID, "gpt-4o", "Hi")
        assertTrue(result.isSuccess)
        val msg = result.getOrThrow().assistantMessage
        assertEquals("Hello!", msg.content)
        assertEquals(15, msg.usage?.totalTokens)
    }

    fun `test export session as markdown`() {
        // Setup session with known messages, export, verify content
    }

    fun `test session persists and reloads`() {
        // sendMessage → close session → loadSession → verify messages intact
    }
}
```

**`src/test/kotlin/.../e2e/ProviderFlowTest.kt`**
```kotlin
class ProviderFlowTest : BasePlatformTestCase() {
    fun `test auth error returns AiProviderError AuthError`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid_api_key"}"""))
        val result = service.sendMessage(PROVIDER_INSTANCE_ID, "gpt-4o", "Hi")
        assertTrue(result.isFailure)
        assertInstanceOf(AiProviderError.AuthError::class.java, result.exceptionOrNull())
    }

    fun `test quota error returns AiProviderError QuotaError`() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"rate_limit"}"""))
        // ...
    }

    fun `test retry succeeds on third attempt`() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody(SUCCESS_BODY).addHeader("Content-Type","application/json"))
        val result = service.sendMessage(...)
        assertTrue(result.isSuccess)
        assertEquals(3, server.requestCount)
    }

    fun `test fallback provider used when primary fails`() {
        // primary → 503, secondary → 200
        // verify response comes from secondary
    }

    fun `test verify() returns models list`() {
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"gpt-4o"},{"id":"gpt-4o-mini"}]}""")
            .addHeader("Content-Type","application/json"))
        val r = runBlocking { provider.verify() }
        assertTrue(r.isSuccess)
        assertTrue(r.getOrThrow().message.contains("2 models"))
    }
}
```

**`src/test/kotlin/.../e2e/ExportFlowTest.kt`**
```kotlin
class ExportFlowTest {
    fun `test latex export contains metadata`()
    fun `test markdown export has yaml frontmatter`()
    fun `test json export is valid and complete`()
    fun `test code only mode extracts only code blocks`()
    fun `test full audit json includes usage per message`()
}
```

### Archivos afectados

| Archivo | Cambio |
|---------|--------|
| `src/test/.../e2e/SessionFlowTest.kt` | **NUEVO** — 3 tests de flujo de sesión |
| `src/test/.../e2e/ProviderFlowTest.kt` | **NUEVO** — 5 tests de provider (errores, retry, fallback, verify) |
| `src/test/.../e2e/ExportFlowTest.kt` | **NUEVO** — 5 tests de export por formato y modo |

### Checkpoints

- **CP-44** `./gradlew test` — PASS: todos los tests nuevos pasan. FAIL: fallos de compilación o assertion.
- **CP-45** `./gradlew test --tests "*.ProviderFlowTest.test retry succeeds on third attempt"` → server.requestCount == 3.
- **CP-46** `./gradlew test --tests "*.ExportFlowTest"` → 5/5 tests green.

---

## Tarea 5.2 — Matriz de compatibilidad IDE en CI

### Contexto
`.run/Run Verifications.run.xml` existe pero no hay CI automatizado.
La dependencia de `intellijIdea("2025.2.6.2")` es una versión única — no hay matrix.

### Diseño

**Nuevo archivo: `.github/workflows/ci.yml`**
```yaml
name: CI

on:
  push:
    branches: [ main, "feature/**" ]
  pull_request:
    branches: [ main ]

jobs:
  build-and-verify:
    strategy:
      matrix:
        ide-version: [ "2025.1", "2025.2.6.2" ]
      fail-fast: false
    runs-on: ubuntu-latest
    name: Build & Verify — ${{ matrix.ide-version }}

    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Build
        run: ./gradlew buildPlugin -PideVersion=${{ matrix.ide-version }}

      - name: Run tests
        run: ./gradlew test

      - name: Verify plugin
        run: ./gradlew verifyPlugin -PideVersion=${{ matrix.ide-version }}

      - name: Upload verification report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: verification-${{ matrix.ide-version }}
          path: build/reports/pluginVerifier/
          retention-days: 7
```

**build.gradle.kts — soporte de `-PideVersion`:**
```kotlin
val ideVersion = providers.gradleProperty("ideVersion").getOrElse("2025.2.6.2")
intellijPlatform {
    intellijIdea(ideVersion)
    ...
}
```

**Limpieza automática de reportes:**
El step `upload-artifact` con `retention-days: 7` gestiona la limpieza automáticamente.
Para reportes locales: añadir `.gitignore` entry para `build/reports/pluginVerifier/`.

### Archivos afectados

| Archivo | Cambio |
|---------|--------|
| `.github/workflows/ci.yml` | **NUEVO** — pipeline con matrix 2 versiones IDE |
| `build.gradle.kts` | + soporte `ideVersion` property |
| `.gitignore` | + `build/reports/pluginVerifier/` |

### Checkpoints

- **CP-47** `./gradlew buildPlugin -PideVersion=2025.1` (local) — PASS: ZIP generado. FAIL: error de resolución de dependencias.
- **CP-48** Push a rama feature/* → GitHub Actions runs → ambas matrices pasan (o falla visible con informe subido).
- **CP-49** `./gradlew verifyPlugin` — reporte en `build/reports/pluginVerifier/` sin errores críticos.

---

## Tarea 5.3 — Telemetría local opcional (latencia, ratio de error por proveedor)

### Diseño — sin dependencias externas, solo in-memory + export local

**Nuevo archivo: `core/telemetry/TelemetryEvent.kt`**
```kotlin
@Serializable
data class TelemetryEvent(
    val timestampMs: Long = System.currentTimeMillis(),
    val providerInstanceId: String,
    val modelId: String,
    val operation: String,       // "chat", "image", "tts", "stt", "verify"
    val latencyMs: Long,
    val success: Boolean,
    val errorType: String? = null,  // class simple name de AiProviderError subtype
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
)
```

**Nuevo archivo: `core/telemetry/LocalTelemetry.kt`**
```kotlin
object LocalTelemetry {
    private val enabled: Boolean get() = AiManagerSettings.isTelemetryEnabled()
    private val ring = ArrayDeque<TelemetryEvent>(maxCapacity = 1000)

    fun record(event: TelemetryEvent) {
        if (!enabled) return
        synchronized(ring) {
            if (ring.size >= 1000) ring.removeFirst()
            ring.addLast(event)
        }
    }

    fun snapshot(): List<TelemetryEvent> = synchronized(ring) { ring.toList() }

    fun statsByProvider(): Map<String, ProviderStats> {
        return snapshot().groupBy { it.providerInstanceId }.mapValues { (_, events) ->
            ProviderStats(
                totalRequests = events.size,
                successRate = events.count { it.success }.toDouble() / events.size,
                avgLatencyMs = events.map { it.latencyMs }.average().toLong(),
                errorBreakdown = events.filter { !it.success }
                    .groupBy { it.errorType ?: "Unknown" }
                    .mapValues { it.value.size },
            )
        }
    }

    fun exportJson(path: Path) {
        Files.writeString(path, Json { prettyPrint = true }.encodeToString(snapshot()))
    }
}

data class ProviderStats(
    val totalRequests: Int,
    val successRate: Double,
    val avgLatencyMs: Long,
    val errorBreakdown: Map<String, Int>,
)
```

**AiManagerService.kt — instrumentación:**
Envolver cada llamada a provider con medición de latencia:
```kotlin
val t0 = System.currentTimeMillis()
val result = provider.chat(request)
LocalTelemetry.record(TelemetryEvent(
    providerInstanceId = providerInstanceId,
    modelId = modelId,
    operation = "chat",
    latencyMs = System.currentTimeMillis() - t0,
    success = result.isSuccess,
    errorType = result.exceptionOrNull()?.javaClass?.simpleName,
))
```

**AiManagerSettingsState.kt:**
```kotlin
var telemetryEnabled: Boolean = false
```

**Settings UI — nueva sección "Metrics" en `AiManagerConfigurable`:**
- Toggle "Enable local telemetry"
- Botón "View metrics" → dialog con tabla por provider: requests, success %, avg latency, errores por tipo
- Botón "Export metrics JSON"

### Archivos afectados

| Archivo | Cambio |
|---------|--------|
| `core/telemetry/TelemetryEvent.kt` | **NUEVO** |
| `core/telemetry/LocalTelemetry.kt` | **NUEVO** |
| `services/AiManagerService.kt` | instrumentar todas las ops con `LocalTelemetry.record` |
| `settings/AiManagerSettingsState.kt` | + `telemetryEnabled: Boolean = false` |
| `settings/AiManagerSettings.kt` | + `isTelemetryEnabled()`, `setTelemetryEnabled()` |
| `settings/ui/AiManagerConfigurable.kt` | + sección Metrics con toggle + botones |

### Checkpoints

- **CP-50** `./gradlew compileKotlin` — PASS: 0 errores.
- **CP-51** Con telemetría habilitada: enviar 3 mensajes → `LocalTelemetry.snapshot().size == 3`.
- **CP-52** "View metrics" dialog → tabla con al menos 1 proveedor con `successRate > 0`.
- **CP-53** "Export metrics JSON" → archivo JSON válido con array de eventos.
- **CP-54** Con telemetría deshabilitada: enviar mensajes → `LocalTelemetry.snapshot().isEmpty()`.

---

## Orden de implementación recomendado

```
5.3 (Telemetría — instrumentar service)  →  5.1 (E2E tests con surface completa)  →  5.2 (CI matrix)
```
Razón: La telemetría instrumenta `AiManagerService` que los tests E2E verifican.
CI matrix se configura al final cuando la suite de tests está completa.
