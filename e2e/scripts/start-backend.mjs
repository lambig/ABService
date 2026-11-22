import { spawn } from 'node:child_process';
import { existsSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../../', import.meta.url));
const database = 'abservice_e2e';
const host = '127.0.0.1';
const port = '5432';
const jdbc = `jdbc:postgresql://${host}:${port}/${database}`;
const reactive = `postgresql://${host}:${port}/${database}`;

export function backendLaunch(env, directory = root) {
  // This path cleans a database. Do not inherit alternate URLs, profiles,
  // config sources or JVM options, even when they appear harmless individually.
  for (const [key, value] of Object.entries(env)) {
    const name = key.replace(/[^a-zA-Z0-9_]/g, '_').toUpperCase();
    const expected = { DB_HOST: [host, 'localhost'], DB_PORT: [port], DB_NAME: [database] };
    if (name in expected && !expected[name].includes(value)) {
      throw new Error(`E2E startup refuses ${key}; use the dedicated local database.`);
    }
    if (
      /(^|_)QUARKUS_(DATASOURCE|FLYWAY|CONFIG|PROFILE)(_|$)/.test(name) ||
      /(^|_)ABSERVICE_DB_/.test(name) ||
      /(^|_)SMALLRYE_CONFIG_/.test(name) ||
      [
        'JAVA_TOOL_OPTIONS',
        'JDK_JAVA_OPTIONS',
        '_JAVA_OPTIONS',
        'JAVA_OPTS',
        'MP_CONFIG_PROFILE',
        'CONFIG_ORDINAL',
      ].includes(name)
    ) {
      throw new Error(`E2E startup refuses configuration override ${key}.`);
    }
  }
  const config = join(directory, 'config');
  if (
    existsSync(join(directory, '.env')) ||
    (existsSync(config) &&
      readdirSync(config).some((name) => /^application(?:-.*)?\.(properties|ya?ml)$/.test(name)))
  ) {
    throw new Error(
      'E2E startup refuses external .env or config/application files. Use explicit test environment variables.',
    );
  }
  const build = join(directory, 'backend/build');
  const jars = readdirSync(build).filter((name) => name.endsWith('-runner.jar'));
  if (jars.length !== 1) {
    throw new Error(
      'E2E startup requires exactly one backend/build/*-runner.jar. Build the backend first.',
    );
  }
  const properties = {
    'quarkus.http.port': '8090',
    'quarkus.datasource.jdbc.url': jdbc,
    'quarkus.datasource.reactive.url': reactive,
    'quarkus.flyway.jdbc-url': jdbc,
    'quarkus.flyway.username': '${quarkus.datasource.username}',
    'quarkus.flyway.password': '${quarkus.datasource.password}',
    'quarkus.flyway.clean-at-start': 'true',
    'abservice.db.host': host,
    'abservice.db.port': port,
    'abservice.db.name': database,
  };
  return {
    args: [
      '-Dquarkus.profile=dev',
      ...Object.entries(properties).flatMap(([key, value]) => [
        `-D${key}=${value}`,
        `-D%dev.${key}=${value}`,
      ]),
      '-jar',
      join(build, jars[0]),
    ],
    options: {
      cwd: directory,
      stdio: 'inherit',
      env: { ...env, DB_HOST: host, DB_PORT: port, DB_NAME: database },
    },
  };
}

function startBackend() {
  const launch = backendLaunch(process.env);
  console.log(`E2E startup: dev profile, port 8090; reset ${host}:${port}/${database}.`);
  const child = spawn('java', launch.args, launch.options);
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
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  startBackend();
}
