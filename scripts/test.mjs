import { spawnSync } from 'node:child_process';
import { readdir } from 'node:fs/promises';
import path from 'node:path';
import { build, maven } from './build.mjs';
import { root, wasmNodeArgs } from './paths.mjs';

if (!process.argv.includes('--wasm-only')) {
  maven(['-B', '-q', '-f', 'testing-9a/Software testing/pom.xml', 'test']);
}
await build();
// Run each file in its own process: Node 24's --test isolates V8 flags needed for Wasm exception references.
const files = (await readdir(path.join(root, 'tests'))).filter(name => name.endsWith('.test.mjs')).map(name => 'tests/' + name);
for (const file of files) {
  const result = spawnSync(process.execPath, [...wasmNodeArgs, file], { cwd: root, stdio: 'inherit' });
  if (result.status !== 0) process.exit(result.status || 1);
}
