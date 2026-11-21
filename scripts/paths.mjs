import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

export const root = fileURLToPath(new URL('../', import.meta.url));
export const webSource = path.join(root, 'web');
export const wasmOutput = path.join(root, 'testing-9a/wasm-runtime/target/wasm');
export const dist = path.join(root, 'dist');
// V8 feature flags are not listed by allowedNodeEnvironmentFlags (which describes NODE_OPTIONS).
const v8Options = spawnSync(process.execPath, ['--v8-options'], { encoding: 'utf8' }).stdout || '';
export const wasmNodeArgs = v8Options.includes('--experimental-wasm-exnref')
  ? ['--experimental-wasm-exnref'] : [];
