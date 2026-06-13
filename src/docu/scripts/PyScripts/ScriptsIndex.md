# Scripts Index

## How to use this index
- Add one compact section per script.
- Keep details in `<script>.md`; keep this file as navigable overview.
- Update this file whenever a script is created, renamed, or removed.

## session

### `safe-write.mjs`
| Type | Synopsis | Input | Output | Params |
|------|----------|-------|--------|--------|
| Node.js CLI | `node src/docu/scripts/PyScripts/session/safe-write.mjs --file <path>` | `stdin` (write mode) or source file (copy mode) | target file + verification log entry | `--file`, `--from`, `--to`, `--verify`, `--lines`, `--allow-empty` |

- Documentation: `src/docu/scripts/PyScripts/session/safe-write.md`
- File: `src/docu/scripts/PyScripts/session/safe-write.mjs`

### `install-hooks.sh`
| Type | Synopsis | Input | Output | Params |
|------|----------|-------|--------|--------|
| Bash CLI | `bash src/docu/scripts/PyScripts/session/install-hooks.sh [--warn-only|--strict]` | local git repo | `core.hooksPath=.githooks` + guard mode config + executable pre-commit hook | `--warn-only`, `--strict` |

- Documentation: `src/docu/scripts/PyScripts/session/install-hooks.md`
- File: `src/docu/scripts/PyScripts/session/install-hooks.sh`

## Chronology

| Date | Script | Category | Context |
|------|--------|----------|---------|
| 2026-06-14 | `safe-write.mjs` | `session` | Initial migration from s4d8 protocol into aimp |
| 2026-06-14 | `install-hooks.sh` | `session` | Enable repository pre-commit safe-write guard |
