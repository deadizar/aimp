<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# AI Manager Changelog

## [Unreleased]
### Added
- Plugin renamed and re-scaffolded as `AI Manager` with updated plugin metadata.
- Core domain model and JSON session persistence (`Session`, `Message`, `Agent`, `TokenUsage`).
- Provider abstraction and implementations for `1min.ai` and OpenAI-compatible APIs.
- Settings UI with provider management and API-key storage via `PasswordSafe`.
- Functional Tool Window with chat/history and provider/model controls.
- LaTeX export engine (full transcript / assistant-only modes).
- Extra tabs for image generation, TTS, and STT wired to provider capabilities.
- chatplayground.ai viability spike documentation with deferred integration decision.
