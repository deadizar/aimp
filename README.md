# AI Manager

`AI Manager` is an IntelliJ Platform plugin for managing AI chat sessions from a Tool Window, with pluggable providers and LaTeX export.

## Current capabilities
- Chat workflow with persisted session history.
- Provider abstraction with:
  - `1min.ai`
  - OpenAI-compatible endpoints.
- Settings page under `Tools > AI Manager` for provider configuration.
- API key storage through IntelliJ `PasswordSafe`.
- Export session to `.tex` in two modes:
  - Full transcript
  - Assistant-only
- Additional tabs for image generation, TTS, and STT.

## Build and verification
```bash
./gradlew test
./gradlew verifyPlugin
./gradlew buildPlugin
```

## Running in IDE sandbox
Use the run configuration:
- `.run/Run Plugin.run.xml`

## Project structure (high-level)
- `src/main/kotlin/com/github/deadizar/aimanager/core/` - domain, sessions, export.
- `src/main/kotlin/com/github/deadizar/aimanager/provider/` - provider contracts and clients.
- `src/main/kotlin/com/github/deadizar/aimanager/settings/` - persisted settings + Settings UI.
- `src/main/kotlin/com/github/deadizar/aimanager/toolWindow/` - chat/history and extra capability tabs.
- `src/docu/` - operational docs, plans, learned notes.
