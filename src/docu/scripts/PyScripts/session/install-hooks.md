# install-hooks.sh

## NOMBRE
`install-hooks.sh` - instala y activa el hook `pre-commit` del repositorio para validar la guardia de `safe-write`.

## SINOPSIS
```bash
bash src/docu/scripts/PyScripts/session/install-hooks.sh
bash src/docu/scripts/PyScripts/session/install-hooks.sh --warn-only
bash src/docu/scripts/PyScripts/session/install-hooks.sh --strict
```

## DESCRIPCION
Este script configura `core.hooksPath` a `.githooks` en la configuracion local del repositorio, asegura permisos de ejecucion para `.githooks/pre-commit` y define el modo del guard en `aimp.safeWriteGuardMode`.

El hook valida que los archivos en `src/docu/` y `src/main/resources/prompts/` staged en commit tengan una entrada de verificacion consistente en `.git/.aimp-safe-write-log.json`.

Modos disponibles:
- `strict` (default): bloquea commit cuando falla la validacion.
- `warn` (`--warn-only`): reporta advertencias, pero no bloquea commit.

## SALIDA
- `core.hooksPath=.githooks` en configuracion local de Git.
- `aimp.safeWriteGuardMode=strict|warn` en configuracion local de Git.
- Hook `pre-commit` habilitado para commits futuros.

## CODIGOS DE SALIDA
| Codigo | Significado |
|--------|-------------|
| `0` | Instalacion correcta |
| `1` | Error de configuracion o conflicto de hooks |

## NAVEGACION
- [ScriptsIndex](../ScriptsIndex.md)
- [README de PyScripts](../README.md)
- [safe-write.mjs](./safe-write.mjs)

