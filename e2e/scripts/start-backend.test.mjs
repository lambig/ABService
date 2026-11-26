import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { backendLaunch } from './start-backend.mjs';

function fixture(t) {
  const directory = mkdtempSync(join(tmpdir(), 'ab-e2e-launch-'));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  mkdirSync(join(directory, 'backend/build'), { recursive: true });
  writeFileSync(join(directory, 'backend/build/test-runner.jar'), 'test placeholder');
  return directory;
}

test('default and explicit local inputs pin JDBC, reactive and Flyway to the same E2E database', (t) => {
  const directory = fixture(t);
  for (const env of [
    {},
    { DB_HOST: 'localhost', DB_PORT: '5432', DB_NAME: 'abservice_e2e' },
    { DB_HOST: '127.0.0.1' },
  ]) {
    const launch = backendLaunch(env, directory);
    for (const profile of ['', '%dev.']) {
      assert.ok(
        launch.args.includes(
          `-D${profile}quarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:5432/abservice_e2e`,
        ),
      );
      assert.ok(
        launch.args.includes(
          `-D${profile}quarkus.datasource.reactive.url=postgresql://127.0.0.1:5432/abservice_e2e`,
        ),
      );
      assert.ok(
        launch.args.includes(
          `-D${profile}quarkus.flyway.jdbc-url=jdbc:postgresql://127.0.0.1:5432/abservice_e2e`,
        ),
      );
      assert.ok(launch.args.includes(`-D${profile}quarkus.flyway.clean-at-start=true`));
    }
    assert.equal(launch.options.env.DB_HOST, '127.0.0.1');
    assert.equal(launch.options.env.DB_PORT, '5432');
    assert.equal(launch.options.env.DB_NAME, 'abservice_e2e');
    assert.equal(launch.options.cwd, directory);
  }
});

test('rejects other hosts, ports and database names before looking for a JAR', () => {
  for (const env of [
    { DB_HOST: 'db.example.test' },
    { DB_PORT: '5433' },
    { DB_NAME: 'abservice' },
    { DB_HOST: '' },
    { db_name: 'abservice_test' },
  ]) {
    assert.throws(() => backendLaunch(env, 'unused-directory'), /refuses/);
  }
});

test('rejects direct, profile-specific and named connection/configuration overrides without logging their values', () => {
  const keys = [
    'QUARKUS_DATASOURCE_JDBC_URL',
    'QUARKUS_DATASOURCE_REACTIVE_URL',
    '_DEV_QUARKUS_DATASOURCE_JDBC_URL',
    'quarkus.datasource.jdbc.url',
    '%dev.quarkus.datasource.reactive.url',
    'QUARKUS_DATASOURCE__OTHER__JDBC_URL',
    'QUARKUS_FLYWAY_JDBC_URL',
    '_DEV_QUARKUS_FLYWAY_JDBC_URL',
    'ABSERVICE_DB_HOST',
    '_DEV_ABSERVICE_DB_NAME',
    'QUARKUS_PROFILE',
    'QUARKUS_CONFIG_LOCATIONS',
    'QUARKUS_CONFIG_PROFILE_PARENT',
    'SMALLRYE_CONFIG_LOCATIONS',
    'MP_CONFIG_PROFILE',
    'CONFIG_ORDINAL',
    'JAVA_TOOL_OPTIONS',
    'JDK_JAVA_OPTIONS',
    '_JAVA_OPTIONS',
    'JAVA_OPTS',
  ];
  for (const key of keys) {
    assert.throws(
      () => backendLaunch({ [key]: 'sensitive-test-value' }, 'unused-directory'),
      (error) => {
        assert.match(error.message, /refuses configuration override/);
        assert.ok(!error.message.includes('sensitive-test-value'));
        return true;
      },
    );
  }
});

test('rejects external dotenv and profile-aware files before Java starts', (t) => {
  const directory = fixture(t);
  mkdirSync(join(directory, 'config'));
  for (const name of [
    '.env',
    'config/application.properties',
    'config/application-dev.properties',
    'config/application.yaml',
    'config/application-dev.yml',
  ]) {
    const path = join(directory, name);
    writeFileSync(path, 'test placeholder');
    assert.throws(() => backendLaunch({}, directory), /refuses external/);
    rmSync(path);
  }
});

test('preserves explicit test credentials and S3 configuration', (t) => {
  const env = {
    DB_USERNAME: 'test-user',
    DB_PASSWORD: 'test-password',
    S3_ENDPOINT_OVERRIDE: 'http://127.0.0.1:9000',
  };
  const launch = backendLaunch(env, fixture(t));
  for (const [key, value] of Object.entries(env)) assert.equal(launch.options.env[key], value);
  for (const profile of ['', '%dev.']) {
    for (const name of ['username', 'password']) {
      assert.ok(
        launch.args.includes(
          `-D${profile}quarkus.flyway.${name}=` + '${quarkus.datasource.' + name + '}',
        ),
      );
    }
  }
  assert.ok(!launch.args.some((arg) => arg.includes(env.DB_PASSWORD)));
});

test('requires exactly one runner JAR', (t) => {
  const directory = fixture(t);
  writeFileSync(join(directory, 'backend/build/another-runner.jar'), 'test placeholder');
  assert.throws(() => backendLaunch({}, directory), /exactly one/);
  rmSync(join(directory, 'backend/build/another-runner.jar'));
  rmSync(join(directory, 'backend/build/test-runner.jar'));
  assert.throws(() => backendLaunch({}, directory), /exactly one/);
});
