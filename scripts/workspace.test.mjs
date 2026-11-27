import assert from 'node:assert/strict';
import test from 'node:test';
import { composeDefinition, validId } from './workspace.mjs';

const state = (id, first) => ({
  root: `/work/repo-${id}`, shared: '/work/repo/.jj/repo', git: '/work/repo/.git',
  id, project: `abdev-fixture-${id}`, uid: 1000, gid: 1000,
  ports: Object.fromEntries(['backend', 'public', 'admin', 'db', 'minio', 'console', 'e2e'].map((key, index) => [key, first + index])),
});

test('two environments own disjoint host ports, sources and project resources', () => {
  const a = composeDefinition(state('alpha', 20000));
  const b = composeDefinition(state('beta', 21000));
  assert.notEqual(a.name, b.name);
  const ports = (config) => config.services.tools.ports.map((entry) => entry.split(':')[1]);
  assert.equal(ports(a).some((port) => ports(b).includes(port)), false);
  for (const config of [a, b]) {
    for (const service of Object.values(config.services)) {
      assert.equal(service.container_name, undefined);
      assert.equal(service.privileged, undefined);
      assert.notEqual(service.network_mode, 'host');
      for (const volume of service.volumes ?? []) assert.notEqual(volume.target, '/var/run/docker.sock');
    }
    for (const volume of Object.values(config.volumes)) assert.equal(volume.name, undefined);
    assert.equal(config.networks, undefined);
    assert.equal(config.services.tools.ports.every((port) => port.startsWith('127.0.0.1:')), true);
    assert.ok(config.services.tools.volumes.some((volume) => volume.source === '/work/repo/.jj/repo'));
    assert.ok(config.services.tools.volumes.some((volume) => volume.source === '/work/repo/.git'));
  }
});

test('browser upload and internal storage share the real MinIO port; test DB safety defaults stay local', () => {
  const s = state('alpha', 20000);
  const config = composeDefinition(s);
  const env = config.services.apps.environment;
  assert.equal(env.S3_ENDPOINT_OVERRIDE, `http://127.0.0.1:${s.ports.minio}`);
  assert.equal(env.E2E_UPLOAD_ORIGIN, env.S3_ENDPOINT_OVERRIDE);
  assert.ok(config.services.minio.command.includes(`:${s.ports.minio}`));
  assert.ok(config.services.tools.ports.includes(`127.0.0.1:${s.ports.minio}:${s.ports.minio}`));
  assert.equal(env.DB_NAME, undefined);
  assert.equal(env.DB_PORT, undefined);
  assert.equal(config.services.postgres.network_mode, 'service:tools');
  assert.equal(env.CORS_ORIGINS, `http://127.0.0.1:${s.ports.public},http://127.0.0.1:${s.ports.admin}`);
});

test('environment IDs cannot become paths or Compose options', () => {
  for (const id of ['../other', '--project-name', '', 'A', 'a/b', 'a\nb', 'a'.repeat(32)]) assert.equal(validId(id), false);
  for (const id of ['a', 'issue-619', 'task2']) assert.equal(validId(id), true);
});
