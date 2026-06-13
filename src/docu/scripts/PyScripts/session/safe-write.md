# safe-write.mjs

## NOMBRE
`safe-write.mjs` - escritura, copia y verificacion seguras con validacion inmediata en disco.

## SINOPSIS
```bash
cat contenido.md | node src/docu/scripts/PyScripts/session/safe-write.mjs --file ruta/al/archivo.md
node src/docu/scripts/PyScripts/session/safe-write.mjs --from origen --to destino
node src/docu/scripts/PyScripts/session/safe-write.mjs --verify ruta/al/archivo.md
```

## DESCRIPCION
`safe-write.mjs` aplica un flujo seguro:

1. escribe en un temporal del mismo directorio;
2. hace `rename` atomico al destino final;
3. relee desde disco y compara byte a byte;
4. imprime resumen (`bytes`, `sha256`, preview y estado Git);
5. registra hash verificado en `.git/.aimp-safe-write-log.json` cuando el archivo esta dentro del repo.

## PARAMETROS / OPCIONES
| Opcion | Tipo | Obligatoria | Significado |
|--------|------|-------------|-------------|
| `--file <ruta>` | string | Si (modo escritura) | Escribe en `<ruta>` el contenido de `stdin` |
| `--from <ruta>` | string | Si (modo copia) | Archivo origen |
| `--to <ruta>` | string | Si (modo copia) | Archivo destino |
| `--verify <ruta>` | string | Si (modo verificacion) | Verifica un archivo existente y registra su hash |
| `--lines <n>` | entero | No | Numero de lineas de preview (default `12`) |
| `--allow-empty` | flag | No | Permite archivos vacios |
| `--help` | flag | No | Muestra ayuda |

## SALIDA
- archivo escrito/copiado/verificado;
- salida de consola con modo, ruta, bytes, hash, preview y estado Git;
- actualizacion de `.git/.aimp-safe-write-log.json` si aplica.

## CODIGOS DE SALIDA
| Codigo | Significado |
|--------|-------------|
| `0` | Operacion correcta |
| `1` | Error de uso o I/O |
| `2` | Fallo de verificacion post-escritura/copia |

## EJEMPLOS
```bash
cat <<'EOF' | node src/docu/scripts/PyScripts/session/safe-write.mjs --file src/docu/notes/demo.md
# Nota segura
EOF

node src/docu/scripts/PyScripts/session/safe-write.mjs --verify src/main/resources/prompts/WarmUpJava.md

node src/docu/scripts/PyScripts/session/safe-write.mjs \
  --from src/main/resources/prompts/WarmUpJava.md \
  --to /tmp/WarmUpJava.backup.md
```

## NAVEGACION
- [ScriptsIndex](../ScriptsIndex.md)
- [README de PyScripts](../README.md)
