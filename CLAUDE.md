# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**aimp** is a JetBrains IntelliJ Platform plugin built with Kotlin and Gradle. It is based on the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template). The project targets IntelliJ IDEA 2025.2.6.2 and uses Java 21.

## Build and Development Commands

```bash
# Build the plugin distribution ZIP
./gradlew buildPlugin

# Run all tests and checks
./gradlew check

# Run plugin structure verification
./gradlew verifyPlugin

# Run the plugin in a sandboxed IDE instance (use the run config instead)
# .run/Run Plugin.run.xml
```

Run configurations are available in `.run/` for IntelliJ IDEA:
- **Run Plugin** — launches a sandboxed IDE with the plugin loaded
- **Run Tests** — executes the test suite
- **Run Verifications** — runs plugin verifier

## Architecture

The plugin registers three extension points in `src/main/resources/META-INF/plugin.xml`:

| Component | Class | Role |
|-----------|-------|------|
| Tool Window | `toolWindow.MyToolWindowFactory` | Creates the "MyToolWindow" panel in the IDE |
| Startup Activity | `startup.MyProjectActivity` | Runs on every project open |
| Project Service | `services.MyProjectService` | Project-scoped singleton, accessed via `project.service<MyProjectService>()` |

`MyBundle` wraps the `messages/MyBundle.properties` resource bundle for i18n.

Tests extend `BasePlatformTestCase` (JUnit 4). Test data files live in `src/test/testData/`.

## Documentation System

All documentation lives under `src/docu/`. Key paths:

| Path | Purpose |
|------|---------|
| `src/docu/learned/INDEX.md` | Knowledge base index — read this at session start |
| `src/docu/planLogs/{branch}/{yyyy}-{mm}/{dd}-{title}.md` | Agent plan logs (immutable backups) |
| `src/docu/diaryLogs/{branch}/{yyyy}-{mm}/{dd}-{title}.md` | Daily diary logs |
| `src/docu/continue/{branch}/{yyyy}-{mm}/{dd}-{title}.md` | Continuation prompts for next session |
| `src/docu/daily/YYYY/MM/ses_YYYY-MM-DD.md` | Session summaries |
| `src/docu/ToDo/YYYY/MM/ses_YYYY-MM-DD_next.md` | Session ToDo checklists |

Prompts used to prime the agent are stored in `src/main/resources/prompts/`. `WarmUpJava.md` is the canonical warm-up protocol for this project.

## Coding Standards

- **Efficiency over readability** when in conflict.
- **Error handling**: Golang-style — capture errors at the lowest level and return them to the caller. Do NOT use exceptions or asserts to control normal flow. Do NOT propagate exceptions across architectural layers.
- All code must compile without avoidable warnings.

## File Write Protocol

After every file creation or modification, verify the on-disk state before considering the edit complete:

```bash
wc -c <file>
sed -n '1,12p' <file>
git diff -- <file>
```

The preferred persistence path for AI agents:

```bash
cat <<'EOF' | node src/docu/scripts/PyScripts/session/safe-write.mjs --file path/to/file.md
...content...
EOF
```

Do NOT rely on IDE editor buffers or tool success messages alone as proof of persistence.

## Plan Robustness — Checkpoint Protocol

Every agent-generated plan must include verifiable checkpoints (`CP-xx`), placed after each batch of file edits, each sync operation, and each `git commit`. Each checkpoint must include:
- A bash command to verify the step
- A PASS criterion and a FAIL criterion

Create a progress file at `src/docu/planLogs/{branch}/{yyyy}-{mm}/{dd}-checkpoint-progress.md` pre-populated with all checkpoints. Update it after each verification. On resumption after an interruption, skip to the first unchecked checkpoint.

## Session Start Checklist

Session-start intent auto-trigger:
- If the user writes phrases like "empezamos una sesion/tarea", "inicia la sesion/tarea", or "prepara/inicia el contexto", first read `src/main/resources/prompts/WarmUpJava.md` and apply its `Session Start - Initial Tasks`.
- Reference skill definition: `src/docu/skills/aimp-session-start/SKILL.md`.

1. Read `src/docu/learned/INDEX.md` for the knowledge base index.
2. Infer the project structure, logic, and component dependencies from the codebase.

## Session End Checklist

1. Write session summary to `src/docu/daily/YYYY/MM/ses_YYYY-MM-DD.md`.
2. Write ToDo list to `src/docu/ToDo/YYYY/MM/ses_YYYY-MM-DD_next.md` (sort by impact: high → medium → low; tag each item `[agent]` or `[user]`).
3. Run the Write Verification Routine on all artifacts created or modified.
4. Update `src/docu/README.md` links.
5. Archive any new/modified scripts as deep copies in `src/docu/scripts/PyScripts/`.
6. Run a residue sweep for local tool artifacts in `/.claude/` and `/.refact/`:
   - Preview first:
	 ```bash
	 git clean -nd -- .claude .refact
	 ```
   - Ask for explicit user confirmation before deletion.
   - After confirmation, remove residue safely:
	 ```bash
	 git clean -fd -- .claude .refact
	 ```
   - If nested leftovers remain, use direct removal only with explicit confirmation:
	 ```bash
	 rm -rf .claude .refact
	 ```
