import { spawn } from 'node:child_process';
import { readdirSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../../', import.meta.url));
const build = join(root, 'backend/build');
const database = 'abservice_e2e';
if (process.env.DB_NAME && process.env.DB_NAME !== database) {
  throw new Error(`E2E startup refuses DB_NAME=${process.env.DB_NAME}; use ${database}.`);
}
const jars = readdirSync(build).filter((name) => name.endsWith('-runner.jar'));
if (jars.length !== 1) {
  throw new Error(
    'E2E startup requires exactly one backend/build/*-runner.jar. Build the backend first.',
  );
}
console.log(`E2E startup: ${jars[0]}, dev profile, port 8090; reset ${database}.`);
const child = spawn(
  'java',
  [
    '-Dquarkus.profile=dev',
    '-Dquarkus.http.port=8090',
    '-Dquarkus.flyway.clean-at-start=true',
    '-jar',
    join(build, jars[0]),
  ],
  { cwd: root, stdio: 'inherit', env: { ...process.env, DB_NAME: database } },
);
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => child.kill(signal));
}
child.on('error', (error) => {
  console.error('E2E startup failed:', error.message);
  process.exitCode = 1;
});
child.on('exit', (code, signal) => {
  process.exitCode = code ?? (signal === 'SIGINT' ? 130 : 143);
});
