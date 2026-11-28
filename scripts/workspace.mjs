import { spawn, spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync, existsSync, statSync, realpathSync } from 'node:fs';
import { createServer } from 'node:net';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptRoot = fileURLToPath(new URL('../', import.meta.url));
export const validId = (id) => typeof id === 'string' && /^[a-z][a-z0-9-]{0,30}$/.test(id);
export const projectName = (git, id) => 'abdev-' + createHash('sha256').update(git).digest('hex').slice(0, 10) + '-' + id;
const run = (command, args, cwd = scriptRoot) => {
  const result = spawnSync(command, args, { cwd, encoding: 'utf8' });
  if (result.error || result.status !== 0) {
    throw new Error(`${command} failed: ${result.error?.message ?? result.stderr}`);
  }
  return result.stdout.trim();
};
const stream = (command, args, cwd = scriptRoot) => new Promise((accept, reject) => {
  const child = spawn(command, args, { cwd, stdio: 'inherit' });
  child.on('error', reject);
  child.on('exit', (code) => code === 0 ? accept() : reject(new Error(`${command} exited ${code}`)));
});

export function composeDefinition(state) {
  const { root, shared, git, project, ports: p } = state;
  const bind = (path) => ({ type: 'bind', source: path, target: path });
  const mounts = [...new Set([root, shared, git])].map(bind);
  const environment = {
    HOME: '/home/developer', GRADLE_USER_HOME: '/home/developer/.gradle',
    ASTRO_TELEMETRY_DISABLED: '1', CI: 'true',
    S3_ENDPOINT_OVERRIDE: `http://127.0.0.1:${p.minio}`,
    CORS_ORIGINS: `http://127.0.0.1:${p.public},http://127.0.0.1:${p.admin}`,
    PUBLIC_SITE_URL: `http://127.0.0.1:${p.public}`,
    API_BASE_URL: 'http://127.0.0.1:8080',
    PUBLIC_API_BASE_URL: '',
    DEV_ASSET_ORIGIN: `http://127.0.0.1:${p.minio}/abservice-assets`,
    E2E_ASSET_ORIGIN: `http://127.0.0.1:${p.minio}/abservice-assets`,
    E2E_UPLOAD_ORIGIN: `http://127.0.0.1:${p.minio}`,
  };
  const tools = {
    build: { context: root, dockerfile: 'docker/workspace/Dockerfile' },
    init: true, user: `${state.uid}:${state.gid}`,
    working_dir: root, environment,
    volumes: [...mounts, { type: 'volume', source: 'home', target: '/home/developer' }],
    cap_drop: ['ALL'], security_opt: ['no-new-privileges:true'],
  };
  const httpHealth = (url) => ({
    test: ['CMD', 'curl', '-fsS', url], interval: '5s', timeout: '3s', retries: 12,
  });
  return {
    name: project,
    services: {
      tools: {
        ...tools, command: ['bash', '-ec', 'bash scripts/setup-workspace-jj.sh && exec sleep infinity'],
        ports: [
          [p.backend, 8080], [p.public, 4321], [p.admin, 4322],
          [p.db, 5432], [p.minio, p.minio], [p.console, p.console], [p.e2e, 8090],
        ].map(([host, container]) => `127.0.0.1:${host}:${container}`),
      },
      postgres: {
        build: { context: join(root, 'docker/postgres') },
        network_mode: 'service:tools',
        depends_on: { tools: { condition: 'service_started' } },
        environment: { POSTGRES_DB: 'abservice', POSTGRES_USER: 'abservice', POSTGRES_PASSWORD: 'abservice' },
        volumes: [
          { type: 'volume', source: 'postgres', target: '/var/lib/postgresql/data' },
          { type: 'bind', source: join(root, 'docker/postgres/init'), target: '/docker-entrypoint-initdb.d', read_only: true },
        ],
        healthcheck: { test: ['CMD', 'pg_isready', '-U', 'abservice', '-d', 'abservice'], interval: '3s', timeout: '3s', retries: 20 },
      },
      minio: {
        build: { context: join(root, 'docker/minio'), target: 'minio' },
        network_mode: 'service:tools',
        depends_on: { tools: { condition: 'service_started' } },
        environment: { MINIO_ROOT_USER: 'minioadmin', MINIO_ROOT_PASSWORD: 'minioadmin123', MINIO_BROWSER_REDIRECT_URL: `http://127.0.0.1:${p.console}` },
        volumes: [{ type: 'volume', source: 'minio', target: '/data' }],
        command: ['server', '/data', '--address', `:${p.minio}`, '--console-address', `:${p.console}`],
        healthcheck: httpHealth(`http://127.0.0.1:${p.minio}/minio/health/live`),
      },
      'minio-init': {
        build: { context: join(root, 'docker/minio'), target: 'mc' },
        network_mode: 'service:tools',
        depends_on: { minio: { condition: 'service_healthy' } },
        entrypoint: ['/bin/sh', '-ec'],
        command: [`mc alias set local http://127.0.0.1:${p.minio} minioadmin minioadmin123; mc mb --ignore-existing local/abservice-assets; mc anonymous set download local/abservice-assets/assets`],
      },
      apps: {
        ...tools, network_mode: 'service:tools',
        depends_on: { postgres: { condition: 'service_healthy' }, 'minio-init': { condition: 'service_completed_successfully' } },
        command: ['node', 'scripts/workspace-apps.mjs'],
        healthcheck: {
          test: ['CMD', 'node', 'scripts/workspace-apps.mjs', '--health'],
          interval: '10s', timeout: '10s', retries: 120, start_period: '30s',
        },
      },
    },
    volumes: { home: {}, postgres: {}, minio: {} },
  };
}

async function freePorts() {
  const servers = [];
  try {
    const ports = {};
    for (const key of ['backend', 'public', 'admin', 'db', 'minio', 'console', 'e2e']) {
      const server = createServer();
      servers.push(server);
      await new Promise((yes, no) => {
        server.once('error', no);
        server.listen(0, '127.0.0.1', yes);
      });
      ports[key] = server.address().port;
    }
    return ports;
  } finally {
    await Promise.all(servers.map((server) => new Promise((yes) => server.close(yes))));
  }
}

function identity(root) {
  const git = realpathSync(run('git', ['-C', root, 'rev-parse', '--path-format=absolute', '--git-common-dir'], root));
  const jjRepo = join(root, '.jj/repo');
  if (!existsSync(jjRepo)) throw new Error('A JJ workspace is required. Initialize a dedicated clone with jj git init first.');
  const shared = statSync(jjRepo).isDirectory() ? realpathSync(jjRepo) : realpathSync(resolve(dirname(jjRepo), readFileSync(jjRepo, 'utf8').trim()));
  return { git, shared };
}

async function initialize(root, id) {
  if (!validId(id)) throw new Error('Environment ID must match [a-z][a-z0-9-]{0,30}.');
  const { git, shared } = identity(root);
  const statePath = join(root, '.dev-env/state.json');
  if (existsSync(statePath)) throw new Error('Workspace already has an environment; use status/up.');
  const registry = join(shared, 'abservice-environments');
  mkdirSync(registry, { recursive: true });
  const reservation = join(registry, `${id}.json`);
  if (existsSync(reservation) && JSON.parse(readFileSync(reservation, 'utf8')).root !== root) {
    throw new Error('Environment ID belongs to another workspace.');
  }
  const state = {
    version: 1, id, root, git, shared,
    project: projectName(git, id),
    ports: await freePorts(), uid: process.getuid(), gid: process.getgid(),
  };
  mkdirSync(join(root, '.dev-env'), { recursive: true });
  writeFileSync(reservation, `${JSON.stringify({ root, project: state.project })}\n`, { flag: existsSync(reservation) ? 'w' : 'wx' });
  writeFileSync(statePath, `${JSON.stringify(state, null, 2)}\n`, { flag: 'wx' });
  writeFileSync(join(root, '.dev-env/compose.json'), `${JSON.stringify(composeDefinition(state), null, 2)}\n`);
  return state;
}

function load(root, id) {
  const state = JSON.parse(readFileSync(join(root, '.dev-env/state.json'), 'utf8'));
  const current = identity(root);
  if (state.version !== 1 || state.id !== id || state.root !== root || state.git !== current.git || state.shared !== current.shared || state.project !== projectName(current.git, id)) {
    throw new Error('Environment identity does not match this workspace.');
  }
  const reservation = JSON.parse(readFileSync(join(current.shared, 'abservice-environments', id + '.json'), 'utf8'));
  if (reservation.root !== root || reservation.project !== state.project) throw new Error('Environment ownership record does not match.');
  const ports = Object.values(state.ports);
  if (ports.length !== 7 || new Set(ports).size !== 7 || ports.some((port) => !Number.isInteger(port) || port < 1024 || port > 65535)) throw new Error('Invalid environment ports.');
  // Configuration is regenerated from verified state, never auto-merged with legacy overrides.
  writeFileSync(join(root, '.dev-env/compose.json'), `${JSON.stringify(composeDefinition(state), null, 2)}\n`);
  return state;
}

function urls(state) {
  const p = state.ports;
  console.log(JSON.stringify({ id: state.id, project: state.project, workspace: state.root,
    backend: `http://127.0.0.1:${p.backend}`, public: `http://127.0.0.1:${p.public}`,
    admin: `http://127.0.0.1:${p.admin}/admin`, minio: `http://127.0.0.1:${p.minio}`,
    console: `http://127.0.0.1:${p.console}`, postgresPort: p.db,
  }, null, 2));
}

async function main([command, id, ...args]) {
  if (process.platform !== 'linux') throw new Error('Run this entry point in WSL2/Linux.');
  if (!validId(id)) throw new Error('Usage: node scripts/workspace.mjs <create|init|up|status|stop|destroy|exec|logs> <id> [args]');
  let root = realpathSync(scriptRoot);
  if (command === 'create') {
    if (args.length > 1) throw new Error('create accepts one parent revision (default main@origin).');
    const destination = `${root}-${id}`;
    run('jj', ['--repository', root, 'workspace', 'add', '--colocate', '--name', id, '--revision', args[0] ?? 'main@origin', destination]);
    root = realpathSync(destination);
    urls(await initialize(root, id));
    return;
  }
  if (command === 'init') { urls(await initialize(root, id)); return; }
  const state = load(root, id);
  const compose = ['compose', '--project-directory', root, '-f', join(root, '.dev-env/compose.json'), '--project-name', state.project];
  switch (command) {
    case 'up':
      await stream('docker', [...compose, 'up', '-d', '--build', '--wait', '--wait-timeout', '1800'], root);
      urls(state); break;
    case 'status':
      await stream('docker', [...compose, 'ps', '--all'], root); urls(state); break;
    case 'stop': await stream('docker', [...compose, 'stop'], root); break;
    case 'destroy':
      // Removes only this project's containers, network and volumes. JJ source/history survive.
      await stream('docker', [...compose, 'down', '--volumes'], root); break;
    case 'exec':
      if (!args.length) throw new Error('exec requires a command.');
      await stream('docker', [...compose, 'exec', '-T', 'tools', ...args], root); break;
    case 'logs': await stream('docker', [...compose, 'logs', '--tail', '100', ...args], root); break;
    default: throw new Error(`Unknown command: ${command}`);
  }
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main(process.argv.slice(2)).catch((error) => { console.error(error.message); process.exitCode = 1; });
}
