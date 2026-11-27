#!/usr/bin/env node
// JJ 0.47.1 passes --no-verify to Git. Restore the installed pre-push check.
import { spawnSync } from 'node:child_process';
import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
const args = process.argv.slice(2);
const pushing = args.includes('push');
const checked = pushing ? args.filter((arg) => arg !== '--no-verify') : args;
const result = spawnSync('/usr/bin/git', [
  ...(pushing ? ['-c', `core.hooksPath=${dirname(fileURLToPath(import.meta.url))}`] : []),
  ...checked,
], { stdio: 'inherit' });
if (result.error) console.error(result.error.message);
process.exitCode = result.status ?? 1;
