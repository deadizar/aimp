---
name: aimp-session-start
description: >
  Detect session-start intents in Spanish and trigger the WarmUpJava context
  bootstrap routine automatically. Use when the user asks to start a session,
  start a task, or prepare/init context.
---

# Skill: aimp-session-start

## Objective
When a user message indicates start-of-session intent, load and apply the directives in:

- `src/main/resources/prompts/WarmUpJava.md`

## Trigger patterns (examples)
Treat these as activation cues (case-insensitive, allow variants):

- "empezamos una sesion"
- "empezamos una tarea"
- "inicia la sesion"
- "inicia una tarea"
- "prepara el contexto"
- "inicia el contexto"
- "arranca sesion"
- "vamos a empezar"

## Mandatory behavior on trigger
1. Read `src/main/resources/prompts/WarmUpJava.md`.
2. Apply `Session Start - Initial Tasks` before deep implementation work.
3. Confirm in the response that warm-up directives were loaded.
4. If documentation indexes are missing, create/update them following WarmUpJava.

## Do not trigger on
- Messages that are clearly mid-session adjustments.
- Isolated verbs without startup intent (e.g., "inicia test" inside an active task).

## Verification command
```bash
sed -n '1,120p' src/main/resources/prompts/WarmUpJava.md
```

## References
- `src/main/resources/prompts/WarmUpJava.md`
- `CLAUDE.md`

