import { spawn } from 'node:child_process';
import { createHash } from 'node:crypto';
import { appendFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';

const root = process.cwd();
const logDir = `${root}/.dev-env/logs`;
const children = new Set();
let stopping = false;
const stop = (code) => {
  if (stopping) return;
  stopping = true;
  for (const child of children) child.kill('SIGTERM');
  setTimeout(() => { for (const child of children) child.kill('SIGKILL'); process.exit(code); }, 5000).unref();
  if (!children.size) process.exit(code);
};
const launch = (name, command, args, cwd = root) => {
  const child = spawn(command, args, { cwd, stdio: ['ignore', 'pipe', 'pipe'], env: process.env });
  children.add(child);
  for (const output of [child.stdout, child.stderr]) output.on('data', (data) => {
    appendFileSync(`${logDir}/${name}.log`, data);
    process.stdout.write(`[${name}] ${data}`);
  });
  child.on('error', (error) => { console.error(error); stop(1); });
  child.on('exit', (code) => {
    children.delete(child);
    if (stopping && !children.size) process.exit(process.exitCode ?? 0);
    if (!stopping && name !== 'install') { process.exitCode = code || 1; stop(process.exitCode); }
  });
  return child;
};

async function health() {
  for (const url of ['http://127.0.0.1:8080/q/health/ready', 'http://127.0.0.1:4321/', 'http://127.0.0.1:4322/admin']) {
    const response = await fetch(url, { signal: AbortSignal.timeout(2500) });
    if (!response.ok) throw new Error(`${url}: ${response.status}`);
  }
}

async function main() {
  if (process.argv.includes('--health')) { await health(); return; }
  mkdirSync(logDir, { recursive: true });
  const fingerprint = createHash('sha256').update(readFileSync('package-lock.json')).update(process.version).update(process.platform).update(process.arch).digest('hex');
  const stamp = 'node_modules/.abservice-workspace-install';
  if (!existsSync(stamp) || readFileSync(stamp, 'utf8') !== fingerprint) {
    const install = launch('install', 'npm', ['ci', '--no-audit', '--no-fund']);
    await new Promise((yes, no) => {
      install.on('error', no);
      install.on('exit', (code) => code === 0 ? yes() : no(new Error(`npm ci exited ${code}`)));
    });
    writeFileSync(stamp, fingerprint);
  }
  launch('backend', 'bash', ['gradlew', '--console=plain', 'quarkusDev', '-Ddebug=false'], `${root}/backend`);
  launch('admin', 'npm', ['run', 'dev', '-w', 'abservice-frontend-admin', '--', '--host', '0.0.0.0']);
  launch('public', 'npm', ['run', 'dev', '-w', 'abservice-frontend-public', '--', '--host', '0.0.0.0']);
}
process.on('SIGTERM', () => stop(0));
process.on('SIGINT', () => stop(0));
main().catch((error) => { console.error(error.message); process.exitCode = 1; stop(1); });
