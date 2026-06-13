# Plan: AI Manager Plugin — Implementación Completa
**Fecha**: 2026-06-14  
**Branch**: main  
**Tipo**: AFK (Away-From-Keyboard, ejecutable por agente sin intervención)  
**Progreso**: [`14-checkpoint-progress.md`](./14-checkpoint-progress.md)

---

## Decisiones de diseño confirmadas

| Parámetro | Decisión |
|-----------|----------|
| Plugin ID | `com.github.deadizar.aimanager` |
| Compatibilidad IDE | Solo 2025.x (última estable) |
| UI Framework | Swing + IntelliJ UI DSL (Kotlin) |
| Proveedores Fase 1 | 1min.ai + base genérica OpenAI-compatible |
| Almacenamiento sesiones | JSON en directorio global (`~/.config/aimanager/`) |
| API Keys | IntelliJ `PasswordSafe` |
| LaTeX export | Diálogo en tiempo de exportación (completo / solo-IA) |
| Token tracking | Desde campo `usage` de la respuesta API |
| Capacidades 1min.ai | LLM chat, Image gen, TTS, STT |
| chatplayground.ai | Spike técnico + decisión en Phase 7 |

---

## Fuentes de referencia investigadas

### 1. CC GUI (jetbrains-cc-gui — MIT)
**Núcleo a reutilizar conceptualmente:**
- Panel lateral con historial de sesiones (lista scrollable)
- Selector de modelo/agente en la parte superior
- Área de chat con mensajes diferenciados (usuario / IA)
- Visualización de tokens consumidos por mensaje/sesión
- Configuración de proveedores accesible desde el panel

### 2. 1min.ai API
**Base URL**: `https://api.1min.ai`  
**Auth**: Header `Authorization: {api_key}`  
**Endpoints clave**:
```
POST /api/features          — Llamada unificada (chat, image, TTS, STT)
GET  /api/models            — Lista de modelos disponibles
GET  /api/me                — Verificación de API key + saldo de créditos
```
**Payload chat** (type = `CHAT_WITH_AI`):
```json
{
  "type": "CHAT_WITH_AI",
  "model": "gpt-4o",
  "conversationId": "optional-uuid",
  "promptObject": {
    "prompt": "...",
    "isMixed": false,
    "webSearch": false
  }
}
```
**Nota**: La API devuelve `usage.promptTokens` / `usage.completionTokens` en la respuesta.

### 3. OpenAI-compatible base
Formato estándar: `POST /v1/chat/completions` con `messages[]`, `model`, `stream`.  
La capa genérica usará este contrato para soportar cualquier endpoint compatible.

### 4. chatplayground.ai (pendiente spike)
No se ha encontrado documentación pública. Estrategia: inspección de tráfico HTTP + análisis de la SPA para detectar endpoints internos. La decisión de implementar se toma tras el spike (Phase 7).

---

## Arquitectura del plugin

```
com.github.deadizar.aimanager/
├── AiManagerBundle.kt             — i18n wrapper
├── AiManagerIcons.kt              — iconos SVG
│
├── core/
│   ├── model/
│   │   ├── Session.kt             — conversación completa
│   │   ├── Message.kt             — mensaje individual (role, content, usage)
│   │   ├── Agent.kt               — modelo+provider configurado
│   │   └── TokenUsage.kt          — prompt/completion/total tokens
│   ├── session/
│   │   ├── SessionManager.kt      — CRUD + active session state
│   │   └── SessionRepository.kt   — leer/escribir JSON en disco
│   └── export/
│       ├── ExportEngine.kt        — orquestador de exportación
│       └── LatexExporter.kt       — renderiza LaTeX2e 2024
│
├── provider/
│   ├── AiProvider.kt              — interface: sendChat / listModels / verify
│   ├── ProviderConfig.kt          — name, baseUrl, apiKeyId, capabilities
│   ├── ProviderRegistry.kt        — lista + factory de providers
│   ├── onemin/
│   │   ├── OneMinProvider.kt      — implementa AiProvider sobre 1min.ai
│   │   └── OneMinApiClient.kt     — HTTP client + DTOs 1min.ai
│   └── openai/
│       ├── OpenAiCompatibleProvider.kt
│       └── OpenAiApiClient.kt     — HTTP client + DTOs OpenAI
│
├── settings/
│   ├── AiManagerSettingsState.kt  — PersistentStateComponent (no-secret fields)
│   ├── AiManagerSettings.kt       — singleton accessor
│   └── ui/
│       ├── AiManagerConfigurable.kt  — entrada en Settings > Tools
│       └── ProviderSettingsPanel.kt  — panel por proveedor
│
├── toolwindow/
│   ├── AiManagerToolWindowFactory.kt
│   ├── AiManagerToolWindow.kt     — contenedor principal (tabs o split)
│   ├── ChatPanel.kt               — panel de chat activo
│   ├── HistoryPanel.kt            — lista de sesiones pasadas
│   └── AgentBar.kt                — barra superior: proveedor + modelo
│
├── services/
│   └── AiManagerService.kt        — application-scoped service
│
└── startup/
    └── AiManagerStartupActivity.kt
```

**Dependencias nuevas a añadir a `build.gradle.kts`:**
```kotlin
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
```

**`plugin.xml` extension points nuevos:**
```xml
<applicationService serviceImplementation="...services.AiManagerService"/>
<applicationConfigurable ... instance="...settings.ui.AiManagerConfigurable"/>
<toolWindow factoryClass="...toolwindow.AiManagerToolWindowFactory" 
            id="AI Manager" anchor="right"/>
<postStartupActivity implementation="...startup.AiManagerStartupActivity"/>
```

---

## Fases y tareas

### PHASE 0 — Scaffold & Rename  *(~1 sesión)*
**Objetivo**: Renombrar completamente el plugin de `aimp` → `aimanager`, limpiar el template, añadir dependencias.

| # | Tarea | Archivos |
|---|-------|---------|
| 0.1 | Actualizar `plugin.xml`: ID, nombre, descripción, vendedor | `META-INF/plugin.xml` |
| 0.2 | Renombrar paquetes Kotlin de `aimp` a `aimanager` | todos los `.kt` en `src/main` |
| 0.3 | Actualizar `settings.gradle.kts`, `gradle.properties`, `build.gradle.kts` | 3 archivos |
| 0.4 | Añadir dependencias: OkHttp, kotlinx.serialization, kotlinx.coroutines | `build.gradle.kts` |
| 0.5 | Añadir plugin kotlinx.serialization al `plugins {}` block | `build.gradle.kts` |
| 0.6 | Renombrar `MyBundle` → `AiManagerBundle`, actualizar `.properties` | 2 archivos |
| 0.7 | Crear `AiManagerIcons.kt` stub | nuevo |
| 0.8 | `./gradlew build` sin errores | — |

> **CP-01** after 0.8

---

### PHASE 1 — Core Domain Model  *(~1 sesión)*
**Objetivo**: Clases de dominio inmutables + serialización + repositorio JSON.

| # | Tarea |
|---|-------|
| 1.1 | `TokenUsage.kt` — data class serializable |
| 1.2 | `Message.kt` — role enum (USER/ASSISTANT/SYSTEM), content, timestamp, usage |
| 1.3 | `Session.kt` — id, title, agentId, messages list, createdAt, updatedAt |
| 1.4 | `Agent.kt` — id, name, providerId, modelId, systemPrompt, parameters |
| 1.5 | `SessionRepository.kt` — load/save/list/delete JSON (usando kotlinx.serialization) |
| 1.6 | `SessionManager.kt` — activeSession, newSession, loadSession, closeSession |
| 1.7 | Tests unitarios para SessionRepository (crear, leer, borrar) |

> **CP-02** after 1.7

---

### PHASE 2 — Provider Abstraction + 1min.ai  *(~2 sesiones)*
**Objetivo**: Capa de abstracción de proveedores + implementación 1min.ai completa + base OpenAI-compatible.

| # | Tarea |
|---|-------|
| 2.1 | `AiProvider.kt` — interface: `suspend fun chat(...)`, `listModels()`, `verify()` |
| 2.2 | `ProviderConfig.kt` — data class con capabilities enum |
| 2.3 | `ProviderRegistry.kt` — registro estático + factory |
| 2.4 | `OneMinApiClient.kt` — OkHttp client, DTOs, manejo de errores HTTP |
| 2.5 | `OneMinProvider.kt` — implementa AiProvider (chat, image, TTS, STT) |
| 2.6 | `OpenAiApiClient.kt` — cliente OpenAI-compatible |
| 2.7 | `OpenAiCompatibleProvider.kt` — implementa AiProvider |
| 2.8 | Tests unitarios con OkHttp MockWebServer para ambos providers |

> **CP-03** after 2.8

---

### PHASE 3 — Settings UI  *(~1 sesión)*
**Objetivo**: Panel de configuración en Settings > Tools > AI Manager.

| # | Tarea |
|---|-------|
| 3.1 | `AiManagerSettingsState.kt` — PersistentStateComponent (sin API keys) |
| 3.2 | `AiManagerSettings.kt` — singleton applicationService |
| 3.3 | `AiManagerConfigurable.kt` — implementa `Configurable` para Settings |
| 3.4 | `ProviderSettingsPanel.kt` — añadir/editar/borrar proveedores, test connection |
| 3.5 | Integración con `PasswordSafe` para almacenar API keys |
| 3.6 | Registrar `applicationConfigurable` en `plugin.xml` |
| 3.7 | Test manual: abrir Settings > Tools > AI Manager, añadir proveedor 1min.ai |

> **CP-04** after 3.7

---

### PHASE 4 — Tool Window UI  *(~2 sesiones)*
**Objetivo**: Panel principal usable: chat activo + historial + selector de agente.

| # | Tarea |
|---|-------|
| 4.1 | `AiManagerToolWindowFactory.kt` — registra tool window "AI Manager" |
| 4.2 | `AgentBar.kt` — ComboBox proveedor + modelo, botón "Nueva sesión" |
| 4.3 | `ChatPanel.kt` — lista de mensajes (JList/JEditorPane), input area, Send button |
| 4.4 | `HistoryPanel.kt` — lista de sesiones pasadas, click para cargar |
| 4.5 | `AiManagerToolWindow.kt` — layout JSplitPane: HistoryPanel | ChatPanel |
| 4.6 | Integrar `AiManagerService` en el ToolWindow (enviar mensajes, recibir respuestas) |
| 4.7 | Display de tokens consumidos por mensaje (en el pie del ChatPanel) |
| 4.8 | Test manual: abrir tool window, chat básico con 1min.ai |

> **CP-05** after 4.8

---

### PHASE 5 — Export LaTeX  *(~1 sesión)*
**Objetivo**: Exportar sesiones a fichero `.tex` (LaTeX2e 2024) con diálogo de opciones.

| # | Tarea |
|---|-------|
| 5.1 | `LatexExporter.kt` — plantilla LaTeX2e, escape de caracteres especiales |
| 5.2 | Dos modos: transcripción completa / solo respuestas IA |
| 5.3 | `ExportEngine.kt` — orquesta exportación, escribe fichero en disco |
| 5.4 | Diálogo de exportación: selector de modo + path de destino |
| 5.5 | Botón "Exportar sesión" en la barra del ChatPanel |
| 5.6 | Test: exportar sesión de prueba y verificar compilación LaTeX |

> **CP-06** after 5.6

---

### PHASE 6 — Capacidades adicionales 1min.ai  *(~1-2 sesiones)*
**Objetivo**: Image generation, TTS, STT desde la UI.

| # | Tarea |
|---|-------|
| 6.1 | Panel de generación de imágenes (prompt + parámetros + resultado inline) |
| 6.2 | Panel TTS (texto → reproducción + descarga) |
| 6.3 | Panel STT (micrófono / fichero → transcripción) |
| 6.4 | Integrar estos paneles como tabs adicionales en el ToolWindow |
| 6.5 | Tests unitarios con MockWebServer para cada endpoint |

> **CP-07** after 6.5

---

### PHASE 7 — Spike chatplayground.ai  *(~1 sesión)*
**Objetivo**: Determinar si chatplayground.ai tiene API accesible programáticamente.

| # | Tarea |
|---|-------|
| 7.1 | Inspección del tráfico HTTP de la webapp (DevTools / mitmproxy) |
| 7.2 | Identificar endpoints de autenticación y conversación |
| 7.3 | Documentar hallazgos en `src/docu/learned/intellij/chatplayground-api-spike.md` |
| 7.4 | Decisión: **implementar** (→ Phase 8) o **diferir** (crear issue tracker) |

> **CP-08** after 7.4 (decisión documentada)

---

### PHASE 8 — (Condicional) chatplayground.ai Provider  *(~2 sesiones)*
*Solo si Phase 7 concluye que la integración es viable.*

| # | Tarea |
|---|-------|
| 8.1 | `ChatPlaygroundApiClient.kt` — autenticación web + API calls |
| 8.2 | `ChatPlaygroundProvider.kt` — implementa AiProvider |
| 8.3 | Añadir al ProviderRegistry |
| 8.4 | Configuración en Settings UI (username/password o token) |
| 8.5 | Tests con MockWebServer |

> **CP-09** after 8.5

---

### PHASE 9 — Polish & Publicación  *(~1 sesión)*

| # | Tarea |
|---|-------|
| 9.1 | Actualizar `CHANGELOG.md` y `README.md` |
| 9.2 | `./gradlew verifyPlugin` sin errores |
| 9.3 | `./gradlew buildPlugin` — generar ZIP |
| 9.4 | Publicar en JetBrains Marketplace (manual) |

> **CP-10** after 9.3

---

## Tabla de checkpoints

| ID | Fase | Verificación | PASS | FAIL |
|----|------|-------------|------|------|
| CP-01 | Phase 0 | `./gradlew build` | BUILD SUCCESSFUL | Cualquier error de compilación |
| CP-02 | Phase 1 | `./gradlew test` | Tests pasan, 0 errores | Test failure o compile error |
| CP-03 | Phase 2 | `./gradlew test` | Tests providers pasan | HTTP mock errors / compile errors |
| CP-04 | Phase 3 | Compilar + Settings UI visible | No errors, panel aparece | Registros faltantes en plugin.xml |
| CP-05 | Phase 4 | Tool window visible, chat funcional | Respuesta IA visible en UI | NPE / missing service / HTTP error |
| CP-06 | Phase 5 | Archivo .tex generado | `pdflatex` compila sin errores | LaTeX inválido / file not written |
| CP-07 | Phase 6 | Tests de panels adicionales | BUILD + tests PASS | Regression en UI o provider |
| CP-08 | Phase 7 | Documento de decisión creado | Fichero MD con conclusión | No documentado |
| CP-09 | Phase 8 | Tests chatplayground | BUILD + tests PASS | Auth failure / API inaccesible |
| CP-10 | Phase 9 | `./gradlew verifyPlugin` | PASSED | Plugin structure errors |

---

## Orden de ejecución recomendado por sesión de agente

```
Sesión 1: Phase 0 + Phase 1          → CP-01, CP-02
Sesión 2: Phase 2 (primera mitad)    → OneMin provider + tests
Sesión 3: Phase 2 (segunda mitad)    → OpenAI provider + CP-03
Sesión 4: Phase 3                    → CP-04
Sesión 5: Phase 4 (primera mitad)    → ToolWindow layout + AgentBar
Sesión 6: Phase 4 (segunda mitad)    → ChatPanel completo + CP-05
Sesión 7: Phase 5                    → CP-06
Sesión 8: Phase 6                    → CP-07
Sesión 9: Phase 7                    → CP-08
Sesión 10: Phase 8 o Phase 9         → CP-09 / CP-10
```

---

## Notas de implementación críticas

### IntelliJ Platform 2025.x APIs
- **UI DSL**: usar `com.intellij.ui.dsl.builder.*` (nueva API, no la deprecated `DialogBuilder`)
- **Tool window**: `ToolWindowFactory.createToolWindowContent(project, toolWindow)`
- **Coroutines**: usar `cs.launch { }` desde `project.coroutineScope` o `ApplicationManager.getApplication().executeOnPooledThread`
- **PasswordSafe**: `PasswordSafe.instance.getPassword(CredentialAttributes(...))` / `setPassword`
- **PersistentStateComponent**: anotar con `@State(name=..., storages=[Storage(...)])`

### Serialización JSON
- Usar `@Serializable` de kotlinx.serialization
- `Json { prettyPrint = true; ignoreUnknownKeys = true }` para el repositorio de sesiones
- Ficheros en: `System.getProperty("user.home") + "/.config/aimanager/sessions/"`

### OkHttp en IntelliJ plugin
- NO usar `OkHttpClient` en el EDT (Event Dispatch Thread)
- Envolver todas las llamadas en `ApplicationManager.getApplication().executeOnPooledThread` o coroutines
- Configurar timeouts: connect=10s, read=60s, write=30s

### LaTeX export
- Escapar correctamente: `\`, `{`, `}`, `$`, `&`, `%`, `#`, `_`, `^`, `~`
- Usar `\verbatim` o `\lstlisting` para bloques de código
- Preámbulo: `\documentclass{article}`, paquetes: `listings`, `xcolor`, `geometry`, `hyperref`, `inputenc`

---

*Plan generado: 2026-06-14 | Sesión de diseño inicial*

