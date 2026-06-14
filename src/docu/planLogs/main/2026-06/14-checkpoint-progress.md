# Checkpoint Progress — AI Manager Plugin
**Plan**: [`14-aimanager-plan.md`](./14-aimanager-plan.md)  
**Branch**: main  
**Creado**: 2026-06-14  
**Reanudación**: comenzar desde el primer checkpoint sin marcar.

---

## Estado de checkpoints

- [x] CP-01 | PASS | 2026-06-14 02:09 | Phase 0 completada; `./gradlew build` OK tras el rename base y el bootstrap del build
- [x] CP-02 | PASS | 2026-06-14 02:10 | Phase 1 completada; `./gradlew test` OK con el modelo de dominio y el repositorio JSON
- [x] CP-03 | PASS | 2026-06-14 03:01 | Phase 2 completada; `./gradlew test` OK con providers 1min.ai y OpenAI-compatible + tests MockWebServer
- [x] CP-04 | PASS | 2026-06-14 03:36 | Phase 3 completada; Settings UI (`AiManagerConfigurable`) registrada y persistencia + PasswordSafe operativos
- [x] CP-05 | PASS | 2026-06-14 03:36 | Phase 4 completada; ToolWindow funcional con historial/chat/agent bar y servicio de aplicación
- [x] CP-06 | PASS | 2026-06-14 03:36 | Phase 5 completada; exportación `.tex` implementada con modos full/assistant y tests de export
- [x] CP-07 | PASS | 2026-06-14 03:36 | Phase 6 completada; IMAGE/TTS/STT integrados en UI y cubiertos en tests del provider 1min.ai
- [x] CP-08 | PASS | 2026-06-14 03:36 | Phase 7 completada; spike documentado en `src/docu/learned/intellij/chatplayground-api-spike.md` con decisión defer
- [x] CP-09 | PASS | 2026-06-14 03:36 | Phase 8 condicional no ejecutada por decisión de Phase 7 (defer explícito y trazable)
- [x] CP-10 | PASS | 2026-06-14 03:36 | Phase 9 completada; `./gradlew verifyPlugin` y `./gradlew buildPlugin` en PASS

---

## Plan Maestro v0.1→v0.2 — Checkpoints (2026-06-14)

> Ver subplanes: [sub00](14-sub00-ux-chat.md) | [sub01](14-sub01-robustez.md) | [sub02](14-sub02-settings.md) | [sub03](14-sub03-multimodal.md) | [sub04](14-sub04-export.md) | [sub05](14-sub05-calidad.md)

### Sub00 — UX Chat y Sesiones
- [ ] CP-S00-00 | Pendiente | `./gradlew compileKotlin` tras añadir `chatStream` a AiProvider
- [ ] CP-S00-01 | Pendiente | UI manual: respuesta progresiva en ChatPanel
- [ ] CP-S00-02 | Pendiente | `./gradlew compileKotlin` tras añadir `pinned` a Session y ops en SessionManager
- [ ] CP-S00-03 | Pendiente | `./gradlew test` — Session deserializa con campo `pinned` por defecto
- [ ] CP-S00-04 | Pendiente | UI manual: rename + pin en HistoryPanel
- [ ] CP-S00-05 | Pendiente | `./gradlew compileKotlin` tras añadir `id` a Message y ops retry/edit
- [ ] CP-S00-06 | Pendiente | `./gradlew test` — Message deserializa con campo `id` por defecto
- [ ] CP-S00-07 | Pendiente | UI manual: Edit+Resend trunca historial correctamente

### Sub01 — Robustez de Proveedores
- [ ] CP-S01-00 | Pendiente | `./gradlew compileKotlin` tras crear RetryPolicy y añadir timeouts a ProviderConfig
- [ ] CP-S01-01 | Pendiente | `./gradlew test` — ProviderEntry deserializa con campos retry por defecto
- [ ] CP-S01-02 | Pendiente | Timeout manual: URL inválida → error en ≤ connect timeout
- [ ] CP-S01-03 | Pendiente | `./gradlew compileKotlin` tras crear AiProviderError
- [ ] CP-S01-04 | Pendiente | UI manual: API key inválida → mensaje "Authentication failed…"
- [ ] CP-S01-05 | Pendiente | UI manual: modelo inválido → mensaje "Model not found…"
- [ ] CP-S01-06 | Pendiente | `./gradlew compileKotlin` tras añadir fallback chain
- [ ] CP-S01-07 | Pendiente | UI manual: fallback automático activo
- [ ] CP-S01-08 | Pendiente | `./gradlew test` — tests existentes no rotos por fallback

### Sub02 — Settings y Seguridad
- [ ] CP-S02-00 | Pendiente | `./gradlew compileKotlin` tras añadir "Test All"
- [ ] CP-S02-01 | Pendiente | UI manual: dialog batch test con ✓/✗ por provider
- [ ] CP-S02-02 | Pendiente | `./gradlew compileKotlin` tras crear ProviderValidator
- [ ] CP-S02-03 | Pendiente | UI manual: label rojo en URL malformada; "Add" bloqueado
- [ ] CP-S02-04 | Pendiente | `./gradlew compileKotlin` tras crear ProviderTestCache y "Diagnose"
- [ ] CP-S02-05 | Pendiente | UI manual: dialog diagnóstico sin revelar API key
- [ ] CP-S02-06 | Pendiente | UI manual: "Last test" actualizado tras "Test Connection"

### Sub03 — Multimodal
- [ ] CP-S03-00 | Pendiente | `./gradlew compileKotlin` tras crear Artifact y añadir a Session
- [ ] CP-S03-01 | Pendiente | `./gradlew test` — Session deserializa con `artifacts = emptyList()`
- [ ] CP-S03-02 | Pendiente | Manual: imagen generada → archivo en `{systemPath}/aimanager/…`
- [ ] CP-S03-03 | Pendiente | `./gradlew compileKotlin` tras preview panels + export changes
- [ ] CP-S03-04 | Pendiente | UI manual: preview imagen en ImagePanel
- [ ] CP-S03-05 | Pendiente | UI manual: "▶ Play" reproduce audio TTS
- [ ] CP-S03-06 | Pendiente | UI manual: transcript editable + copy
- [ ] CP-S03-07 | Pendiente | Export con imagen → .tex incluye `\includegraphics`
- [ ] CP-S03-08 | Pendiente | `./gradlew compileKotlin` tras selector idioma STT
- [ ] CP-S03-09 | Pendiente | Log HTTP: petición STT incluye `language=es`
- [ ] CP-S03-10 | Pendiente | Manual: transcript limpia sin espacios dobles

### Sub04 — Export y Conocimiento
- [ ] CP-S04-00 | Pendiente | `./gradlew compileKotlin` tras ExportMetadata
- [ ] CP-S04-01 | Pendiente | .tex exportado contiene tabla de metadatos
- [ ] CP-S04-02 | Pendiente | Coste estimado gpt-4o-mini: ~$0.00045 para 1000p+500c tokens
- [ ] CP-S04-03 | Pendiente | `./gradlew compileKotlin` tras MarkdownExporter y JsonExporter
- [ ] CP-S04-04 | Pendiente | Export MD → frontmatter YAML válido
- [ ] CP-S04-05 | Pendiente | Export JSON → válido + campo tokens presente
- [ ] CP-S04-06 | Pendiente | `./gradlew compileKotlin` tras nuevos ExportMode
- [ ] CP-S04-07 | Pendiente | CODE_ONLY → solo bloques de código en export
- [ ] CP-S04-08 | Pendiente | FULL_AUDIT JSON → usage por mensaje

### Sub05 — Calidad y Release
- [ ] CP-S05-00 | Pendiente | `./gradlew test` — nuevos E2E tests pasan
- [ ] CP-S05-01 | Pendiente | `ProviderFlowTest.retry` → server.requestCount == 3
- [ ] CP-S05-02 | Pendiente | `ExportFlowTest` → 5/5 green
- [ ] CP-S05-03 | Pendiente | `./gradlew buildPlugin -PideVersion=2025.1` — ZIP generado
- [ ] CP-S05-04 | Pendiente | Push → GitHub Actions matrix pasa
- [ ] CP-S05-05 | Pendiente | `verifyPlugin` — sin errores críticos
- [ ] CP-S05-06 | Pendiente | `./gradlew compileKotlin` tras telemetría
- [ ] CP-S05-07 | Pendiente | 3 mensajes → `snapshot().size == 3`
- [ ] CP-S05-08 | Pendiente | "View metrics" → tabla con stats
- [ ] CP-S05-09 | Pendiente | "Export metrics JSON" → JSON válido
- [ ] CP-S05-10 | Pendiente | Telemetría deshabilitada → `snapshot().isEmpty()`

---

## Formato de actualización

Al completar un checkpoint, reemplazar la línea con:

```
- [x] CP-xx | PASS | 2026-06-DD HH:MM | {nota breve}
```

o en caso de fallo:

```
- [ ] CP-xx | FAIL | 2026-06-DD HH:MM | {razón del fallo}
```
