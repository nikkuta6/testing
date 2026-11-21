import { spawnSync } from 'node:child_process';
import { cp, mkdir, readFile, stat, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { root, webSource, wasmOutput, dist } from './paths.mjs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export function maven(args) {
  const windows = process.platform === 'win32';
  // Only fixed build arguments are passed to cmd; quote the one source-module path containing a space.
  if (windows && args.some(arg => !/^[A-Za-z0-9 /_.=-]+$/.test(arg))) throw new Error('Unexpected Maven build argument');
  const result = windows
    ? spawnSync('mvn.cmd ' + args.map(arg => '"' + arg + '"').join(' '), { cwd: root, stdio: 'inherit', shell: true })
    : spawnSync('mvn', args, { cwd: root, stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error('Maven failed with exit code ' + result.status);
}

export async function build({ compile = true } = {}) {
  if (compile) maven(['-B', '-q', '-f', 'testing-9a/wasm-runtime/pom.xml', 'package']);
  await mkdir(path.join(dist, 'wasm'), { recursive: true });
  await cp(webSource, dist, { recursive: true });
  for (const file of ['app.wasm', 'app.wasm-runtime.mjs']) {
    await cp(path.join(wasmOutput, file), path.join(dist, 'wasm', file));
  }
  const bytes = await readFile(path.join(dist, 'wasm/app.wasm'));
  if (bytes.readUInt32LE(0) !== 0x6d736100) throw new Error('Build did not produce a WebAssembly binary');
  await writeFile(path.join(dist, 'build.json'), JSON.stringify({
    engine: 'WebAssembly GC', compiler: 'TeaVM 0.16.0',
    sha256: createHash('sha256').update(bytes).digest('hex'), bytes: (await stat(path.join(dist, 'wasm/app.wasm'))).size
  }, null, 2) + '\n');
  console.log(`Built dist/: ${(bytes.length / 1024).toFixed(0)} KiB WebAssembly. Start with npm start.`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await build({ compile: !process.argv.includes('--copy-only') });
}
