# PyScripts Archive

This directory is the durable script archive for AI/agent workflows in this repository.

## Purpose
- Preserve reproducible utilities used during development sessions.
- Keep a stable, searchable knowledge layer for operational scripts.
- Store script docs next to scripts using a man-style format.

## Structure
```text
src/docu/scripts/PyScripts/
|- README.md
|- ScriptsIndex.md
|- session/
|  |- safe-write.mjs
|  `- safe-write.md
`- <category>/
   |- <script>
   `- <script>.md
```

## Rules
- Every new or modified script must be documented and indexed.
- If canonical script source is outside this tree, deep-copy it here after updates.
- Always run the write verification routine after script edits/copies.
- Keep names in kebab-case when possible.

## Write Verification Routine
```bash
wc -c <file>
sed -n '1,12p' <file>
git diff -- <file>
```

For exact-copy checks:
```bash
sha256sum source target
cmp -s source target && echo OK
```

## Related files
- `src/docu/scripts/PyScripts/ScriptsIndex.md`
- `src/docu/scripts/PyScripts/session/safe-write.mjs`
- `src/docu/scripts/PyScripts/session/safe-write.md`
- `src/docu/scripts/PyScripts/session/install-hooks.sh`
- `src/docu/scripts/PyScripts/session/install-hooks.md`

## Hook guard install
```bash
bash src/docu/scripts/PyScripts/session/install-hooks.sh
bash src/docu/scripts/PyScripts/session/install-hooks.sh --warn-only
bash src/docu/scripts/PyScripts/session/install-hooks.sh --strict
```
