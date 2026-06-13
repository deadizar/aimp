#!/usr/bin/env node
/**
 * safe-write.mjs
 *
 * Escritura y copia segura de archivos con verificación inmediata en disco.
 * Además registra una marca verificable por hash para integrarse con el hook pre-commit.
 */
import { createHash } from "node:crypto";
import { spawnSync } from "node:child_process";
import { promises as fs } from "node:fs";
import path from "node:path";
import process from "node:process";
const EXIT_USAGE = 1;
const EXIT_VERIFY = 2;
const DEFAULT_PREVIEW_LINES = 12;
const LOG_RELATIVE_PATH = path.join('.git', '.aimp-safe-write-log.json');
function printHelp() {
  console.log(`safe-write.mjs — escritura/copia segura con verificación inmediata
Uso:
  cat contenido.md | node src/docu/scripts/PyScripts/session/safe-write.mjs --file ruta/al/archivo.md
  node src/docu/scripts/PyScripts/session/safe-write.mjs --from origen --to destino
  node src/docu/scripts/PyScripts/session/safe-write.mjs --verify ruta/al/archivo.md
Opciones:
  --file <ruta>        Escribe el contenido leído desde stdin en <ruta>
  --from <ruta>        Archivo origen para copia segura
  --to <ruta>          Archivo destino para copia segura
  --verify <ruta>      Solo verifica el archivo existente en disco
  --lines <n>          Número de líneas de preview (por defecto: 12)
  --allow-empty        Permite escrituras/copias vacías
  --help               Muestra esta ayuda
`);
}
function parseArgs(argv) {
  const options = {
    file: null,
    from: null,
    to: null,
    verify: null,
    lines: DEFAULT_PREVIEW_LINES,
    allowEmpty: false,
    help: false
  };
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === '--file') options.file = argv[++index] ?? null;
    else if (arg === '--from') options.from = argv[++index] ?? null;
    else if (arg === '--to') options.to = argv[++index] ?? null;
    else if (arg === '--verify') options.verify = argv[++index] ?? null;
    else if (arg === '--lines') options.lines = Number.parseInt(argv[++index] ?? '', 10);
    else if (arg === '--allow-empty') options.allowEmpty = true;
    else if (arg === '--help' || arg === '-h') options.help = true;
    else return { ok: false, error: `Argumento desconocido: ${arg}` };
  }
  if (options.help) return { ok: true, options };
  if (!Number.isInteger(options.lines) || options.lines < 1) {
    return { ok: false, error: '--lines debe ser un entero positivo' };
  }
  const usingWrite = Boolean(options.file);
  const usingCopy = Boolean(options.from || options.to);
  const usingVerify = Boolean(options.verify);
  const modeCount = [usingWrite, usingCopy, usingVerify].filter(Boolean).length;
  if (modeCount !== 1) {
    return { ok: false, error: 'Debes elegir exactamente un modo: --file, --from/--to o --verify' };
  }
  if (usingCopy && (!options.from || !options.to)) {
    return { ok: false, error: 'La copia segura requiere --from <ruta> y --to <ruta>' };
  }
  return { ok: true, options };
}
async function readAllStdin() {
  const chunks = [];
  for await (const chunk of process.stdin) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  }
  return Buffer.concat(chunks);
}
function digest(buffer) {
  return createHash('sha256').update(buffer).digest('hex');
}
function previewLines(buffer, maxLines) {
  const text = buffer.toString('utf8');
  const lines = text.split(/\r?\n/).slice(0, maxLines);
  return lines.map((line, index) => `${String(index + 1).padStart(2, '0')} | ${line}`);
}
async function writeAtomic(targetPath, data, mode = null) {
  const absoluteTarget = path.resolve(targetPath);
  const targetDir = path.dirname(absoluteTarget);
  const targetBase = path.basename(absoluteTarget);
  const tempPath = path.join(targetDir, `.${targetBase}.safe-write-${process.pid}-${Date.now()}.tmp`);
  await fs.mkdir(targetDir, { recursive: true });
  try {
    await fs.writeFile(tempPath, data);
    if (mode != null) {
      await fs.chmod(tempPath, mode);
    }
    await fs.rename(tempPath, absoluteTarget);
  } catch (error) {
    try {
      await fs.rm(tempPath, { force: true });
    } catch {
      // no-op defensivo
    }
    throw error;
  }
  return absoluteTarget;
}
async function readBack(targetPath) {
  return fs.readFile(path.resolve(targetPath));
}
function findGitRoot(targetPath) {
  const absoluteTarget = path.resolve(targetPath);
  const cwd = path.dirname(absoluteTarget);
  const probe = spawnSync('git', ['-C', cwd, 'rev-parse', '--show-toplevel'], { encoding: 'utf8' });
  if (probe.status !== 0) return null;
  const root = probe.stdout.trim();
  return root || null;
}
async function loadVerificationLog(gitRoot) {
  const logPath = path.join(gitRoot, LOG_RELATIVE_PATH);
  try {
    const raw = await fs.readFile(logPath, 'utf8');
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object') return { version: 1, entries: {} };
    if (!parsed.entries || typeof parsed.entries !== 'object') parsed.entries = {};
    return parsed;
  } catch {
    return { version: 1, entries: {} };
  }
}
async function saveVerificationLog(gitRoot, log) {
  const logPath = path.join(gitRoot, LOG_RELATIVE_PATH);
  await fs.mkdir(path.dirname(logPath), { recursive: true });
  await fs.writeFile(logPath, JSON.stringify(log, null, 2) + '\n', 'utf8');
}
async function recordVerification(filePath, buffer, mode) {
  const gitRoot = findGitRoot(filePath);
  if (!gitRoot) return null;
  const absoluteTarget = path.resolve(filePath);
  const relative = path.relative(gitRoot, absoluteTarget);
  if (relative.startsWith('..')) return null;
  const log = await loadVerificationLog(gitRoot);
  log.updatedAt = new Date().toISOString();
  log.entries[relative] = {
    sha256: digest(buffer),
    bytes: buffer.length,
    verifiedAt: new Date().toISOString(),
    mode
  };
  await saveVerificationLog(gitRoot, log);
  return { gitRoot, relative, entry: log.entries[relative] };
}
function gitStatus(filePath) {
  const gitRoot = findGitRoot(filePath);
  if (!gitRoot) return null;
  const absoluteTarget = path.resolve(filePath);
  const relative = path.relative(gitRoot, absoluteTarget);
  if (relative.startsWith('..')) return null;
  const probe = spawnSync('git', ['-C', gitRoot, 'status', '--short', '--', relative], { encoding: 'utf8' });
  return {
    root: gitRoot,
    relative,
    status: probe.status === 0 ? probe.stdout.trim() : ''
  };
}
function printSummary({ mode, filePath, buffer, lines, verificationRecord }) {
  const absoluteTarget = path.resolve(filePath);
  console.log('OK safe-write');
  console.log(`mode: ${mode}`);
  console.log(`file: ${absoluteTarget}`);
  console.log(`bytes: ${buffer.length}`);
  console.log(`sha256: ${digest(buffer)}`);
  const preview = previewLines(buffer, lines);
  console.log('preview:');
  if (preview.length === 0) console.log('<empty>');
  for (const line of preview) console.log(line);
  const git = gitStatus(absoluteTarget);
  if (git) {
    console.log(`git: ${git.status || '(sin cambios visibles o no versionado)'}`);
  }
  if (verificationRecord?.relative) {
    console.log(`verification-log: ${verificationRecord.relative}`);
  }
}
async function main() {
  const parsed = parseArgs(process.argv.slice(2));
  if (!parsed.ok) {
    console.error(`ERROR: ${parsed.error}`);
    printHelp();
    process.exitCode = EXIT_USAGE;
    return;
  }
  const { options } = parsed;
  if (options.help) {
    printHelp();
    return;
  }
  try {
    if (options.verify) {
      const verified = await readBack(options.verify);
      if (verified.length === 0 && !options.allowEmpty) {
        console.error('ERROR: el archivo verificado está vacío; usa --allow-empty si es intencional');
        process.exitCode = EXIT_VERIFY;
        return;
      }
      const verificationRecord = await recordVerification(options.verify, verified, 'verify');
      printSummary({ mode: 'verify', filePath: options.verify, buffer: verified, lines: options.lines, verificationRecord });
      return;
    }
    if (options.file) {
      const stdinData = await readAllStdin();
      if (stdinData.length === 0 && !options.allowEmpty) {
        console.error('ERROR: stdin llegó vacío; usa --allow-empty si realmente quieres escribir un archivo vacío');
        process.exitCode = EXIT_USAGE;
        return;
      }
      await writeAtomic(options.file, stdinData);
      const written = await readBack(options.file);
      if (!written.equals(stdinData)) {
        console.error('ERROR: la lectura posterior no coincide con el contenido escrito');
        process.exitCode = EXIT_VERIFY;
        return;
      }
      const verificationRecord = await recordVerification(options.file, written, 'write');
      printSummary({ mode: 'write', filePath: options.file, buffer: written, lines: options.lines, verificationRecord });
      return;
    }
    const sourceData = await readBack(options.from);
    if (sourceData.length === 0 && !options.allowEmpty) {
      console.error('ERROR: el archivo origen está vacío; usa --allow-empty si es intencional');
      process.exitCode = EXIT_USAGE;
      return;
    }
    let sourceMode = null;
    try {
      sourceMode = (await fs.stat(path.resolve(options.from))).mode;
    } catch {
      sourceMode = null;
    }
    await writeAtomic(options.to, sourceData, sourceMode);
    const copied = await readBack(options.to);
    if (!copied.equals(sourceData)) {
      console.error('ERROR: la copia verificada no coincide byte a byte con el origen');
      process.exitCode = EXIT_VERIFY;
      return;
    }
    const verificationRecord = await recordVerification(options.to, copied, 'copy');
    printSummary({ mode: 'copy', filePath: options.to, buffer: copied, lines: options.lines, verificationRecord });
  } catch (error) {
    console.error(`ERROR: ${error?.message || String(error)}`);
    process.exitCode = EXIT_USAGE;
  }
}
main();
