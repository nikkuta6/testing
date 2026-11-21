import { readFile } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import { wasmNodeArgs, root } from './paths.mjs';

if (wasmNodeArgs.some(arg => !process.execArgv.includes(arg))) {
  const result = spawnSync(process.execPath, [...wasmNodeArgs, ...process.argv.slice(1)], { cwd: root, stdio: 'inherit' });
  if (result.error) throw result.error;
  process.exit(result.status ?? 1);
}
const filename = process.argv[2];
if (!filename) { console.error('Usage: npm run run -- program.txt [--debug]'); process.exit(1); }
const { loadRuntime } = await import('../dist/wasm-loader.mjs');
const { exports: wasm } = await loadRuntime();
const result = JSON.parse(wasm.run(await readFile(filename, 'utf8'), process.argv.includes('--debug')));
if (process.argv.includes('--debug')) console.log(JSON.stringify(result, null, 2));
else {
  process.stdout.write(result.output || '');
  if (result.syntaxError || result.runtimeError) { console.error(result.syntaxError || result.runtimeError); process.exitCode = 1; }
}
