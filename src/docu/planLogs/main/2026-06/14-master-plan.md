# Plan Maestro — AI Manager v0.1 → v0.2
**Fecha:** 2026-06-14  
**Rama:** `main`  
**Estado:** En planificación

---

## Visión general

Seis áreas de mejora organizadas en orden de impacto/riesgo. Cada área tiene su propio subplan
con checkpoints verificables. El orden recomendado de implementación sigue dependencias técnicas:

```
Plan 2 (Settings/Seguridad)  →  Plan 1 (Robustez)  →  Plan 0 (UX Chat)
Plan 3 (Multimodal)          →  Plan 4 (Export)     →  Plan 5 (Calidad)
```

**Fundamento del orden:**
- Plan 2 primero: validación y diagnóstico de providers antes de añadir complejidad.
- Plan 1 segundo: robustez (errores normalizados, retry, fallback) que el resto necesita.
- Plan 0 tercero: streaming requiere que AiProvider tenga una superficie robusta.
- Plan 3 y 4 en paralelo (no se bloquean entre sí).
- Plan 5 al final: los tests E2E cubren el comportamiento final de todos los planes anteriores.

---

## Resumen de subplanes

| # | Área | Subplan | Clases nuevas | Clases modificadas | Complejidad |
|---|------|---------|--------------|-------------------|-------------|
| 0 | UX Chat y sesiones | [14-sub00-ux-chat.md](14-sub00-ux-chat.md) | 1 | 7 | Alta |
| 1 | Robustez providers | [14-sub01-robustez.md](14-sub01-robustez.md) | 2 | 5 | Media |
| 2 | Settings y seguridad | [14-sub02-settings.md](14-sub02-settings.md) | 1 | 2 | Baja |
| 3 | Multimodal | [14-sub03-multimodal.md](14-sub03-multimodal.md) | 1 | 5 | Media |
| 4 | Export y conocimiento | [14-sub04-export.md](14-sub04-export.md) | 2 | 4 | Baja |
| 5 | Calidad y release | [14-sub05-calidad.md](14-sub05-calidad.md) | 3 | 2 | Media |

---

## Dependencias entre subplanes

```
sub01 (AiProviderError)  ──►  sub00 (ChatPanel muestra errores tipados)
sub01 (fallback chain)   ──►  sub00 (streaming con fallback)
sub02 (validación)       ──►  sub01 (validator reutilizable en retry)
sub03 (Artifact model)   ──►  sub04 (export con adjuntos)
sub01 (Usage/TokenUsage) ──►  sub04 (metadatos: tokens, coste)
sub00+sub01+sub03+sub04  ──►  sub05 (tests E2E cubren superficie completa)
```

---

## Estado de checkpoints

Ver: [14-checkpoint-progress.md](14-checkpoint-progress.md)

---

## Archivos clave del estado actual

| Archivo | Rol | Limitación actual |
|---------|-----|-------------------|
| `provider/AiProvider.kt` | Interfaz de provider | Sin streaming, sin error tipado |
| `provider/openai/OpenAiApiClient.kt` | Cliente HTTP OpenAI | `stream=false` hardcoded, sin retry |
| `services/AiManagerService.kt` | Orquestador | `runBlocking` en main thread risk, sin fallback |
| `toolWindow/ChatPanel.kt` | UI de chat | Sin streaming, `JBTextArea` plano, sin Retry/Edit |
| `toolWindow/HistoryPanel.kt` | Lista de sesiones | Sin rename, sin pin, sin búsqueda |
| `core/export/LatexExporter.kt` | Export LaTeX | Sin metadatos, sin plantillas CODE_ONLY/AUDIT |
| `settings/ui/ProviderSettingsPanel.kt` | Settings UI | Sin batch test, sin diagnóstico, sin validación proactiva |
| `core/model/Session.kt` | Modelo de sesión | Sin `pinned`, sin `artifacts` |
| `core/model/Message.kt` | Modelo de mensaje | Sin `id` estable, sin `artifacts` |
