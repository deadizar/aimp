#!/usr/bin/env bash
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
current_hooks_path="$(git config --local --get core.hooksPath || true)"
guard_mode="strict"

for arg in "$@"; do
  case "$arg" in
    --warn-only) guard_mode="warn" ;;
    --strict) guard_mode="strict" ;;
    *)
      echo "ERROR: unknown argument '$arg'" >&2
      echo "Usage: bash src/docu/scripts/PyScripts/session/install-hooks.sh [--warn-only|--strict]" >&2
      exit 1
      ;;
  esac
done

if [[ -n "$current_hooks_path" && "$current_hooks_path" != ".githooks" ]]; then
  echo "ERROR: core.hooksPath is already set to '$current_hooks_path'" >&2
  echo "Refusing to override automatically." >&2
  echo "If you want to force this repository to use .githooks, run:" >&2
  echo "  git config --local core.hooksPath .githooks" >&2
  exit 1
fi

if [[ ! -f "$repo_root/.githooks/pre-commit" ]]; then
  echo "ERROR: Missing hook script: $repo_root/.githooks/pre-commit" >&2
  exit 1
fi

chmod +x "$repo_root/.githooks/pre-commit"
git config --local core.hooksPath .githooks
git config --local aimp.safeWriteGuardMode "$guard_mode"

echo "Installed repository hooks."
echo "core.hooksPath=$(git config --local --get core.hooksPath)"
echo "aimp.safeWriteGuardMode=$(git config --local --get aimp.safeWriteGuardMode)"
echo "Hook enabled: .githooks/pre-commit"

