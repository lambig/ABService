import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { runInNewContext } from 'node:vm';
import test from 'node:test';

const workflow = readFileSync(new URL('../../.github/workflows/deploy.yml', import.meta.url), 'utf8');
const steps = workflow.split('\n      - name: ').slice(1);
const step = (name) => {
  const matches = steps.filter((value) => value.startsWith(`${name}\n`));
  assert.equal(matches.length, 1, name);
  return matches[0];
};
const script = (block) => {
  const lines = block.match(/        run: \|\n((?:          .*\n|\n)+)/);
  assert.ok(lines, 'executable workflow script is present');
  return lines[1].replace(/^          /gm, '');
};
const lookup = step('Check whether the image for this commit already exists');
const resolve = step('Resolve the image digest');
const verify = step('Verify the retained image platform before deployment');
const digest = `sha256:${'a'.repeat(64)}`;
const image = `registry.example.test/backend@${digest}`;
const expectedArch = (target) => runInNewContext(
  verify.match(/EXPECTED_ARCH: \$\{\{ (.+) \}\}/)[1], { vars: { BACKEND_DEPLOY_TARGET: target } });

// Execute the actual workflow shell blocks. Only the external AWS/Docker boundaries are mocked.
const check = ({ action, target, platform, pullStatus = 0, inspectStatus = 0 }) => spawnSync(
  process.env.TEST_BASH || 'bash', ['--noprofile', '--norc', '-e', '-o', 'pipefail', '-c', `
aws() {
  [[ "$1 $2" == "ecr describe-images" ]] || return 91
  if [[ "$*" == *"--query"* ]]; then printf '%s\\n' "$DIGEST"; fi
}
docker() {
  printf 'DOCKER:%s\\n' "$*" >&2
  case "$1" in
    pull) return "$PULL_STATUS" ;;
    image) printf '%s\\n' "$MOCK_PLATFORM"; return "$INSPECT_STATUS" ;;
    *) return 92 ;;
  esac
}
${script(lookup)}
${script(resolve)}
${script(verify)}
echo HOST_DEPLOY_ALLOWED
`], { encoding: 'utf8', env: { ...process.env,
    ACTION: action, REGISTRY: 'registry.example.test', REPOSITORY: 'backend', TAG: `sha-${'b'.repeat(40)}`,
    DIGEST: digest, EXPECTED_ARCH: expectedArch(target), MOCK_PLATFORM: platform,
    PULL_STATUS: String(pullStatus), INSPECT_STATUS: String(inspectStatus),
    GITHUB_OUTPUT: '/dev/null', GITHUB_STEP_SUMMARY: '/dev/null',
  } });

test('retained digest verification is mandatory before both host dispatches', () => {
  assert.doesNotMatch(verify, /^        (?:if|continue-on-error):/m);
  const position = workflow.indexOf(verify);
  assert.ok(position > workflow.indexOf(resolve));
  for (const name of ['Deploy via SSM Run Command', 'Deploy cohost via fixed SSM entrypoint']) {
    assert.ok(position < workflow.indexOf(step(name)));
  }
  for (const name of ['Build backend', 'Build image', 'Verify the image architecture matches the host', 'Push image']) {
    const expression = step(name).match(/^        if: (.+)$/m)[1];
    assert.equal(runInNewContext(expression, { steps: { existing: { outputs: { exists: 'true' } } } }), false);
  }
});

for (const action of ['release', 'rollback']) {
  for (const target of ['', 'ec2-arm64', 'cohost-amd64']) {
    const arch = target === 'cohost-amd64' ? 'amd64' : 'arm64';
    test(`${action}: retained image matches ${target || 'default arm64'} without rebuild`, () => {
      const result = check({ action, target, platform: `linux/${arch}` });
      assert.equal(result.status, 0, result.error?.message || result.stderr);
      assert.match(result.stdout, /Reusing the image already built/);
      assert.match(result.stdout, /HOST_DEPLOY_ALLOWED/);
      assert.deepEqual(result.stderr.trim().split('\n'), [
        `DOCKER:pull --platform linux/${arch} ${image}`,
        `DOCKER:image inspect ${image} --format {{.Os}}/{{.Architecture}}`,
      ]);
    });
    test(`${action}: retained image with opposite architecture rejects ${target || 'default arm64'}`, () => {
      const result = check({ action, target, platform: `linux/${arch === 'amd64' ? 'arm64' : 'amd64'}` });
      assert.equal(result.status, 1, result.error?.message || result.stderr);
      assert.match(result.stderr, /release\/rollback refused without rebuilding/);
      assert.doesNotMatch(result.stdout, /HOST_DEPLOY_ALLOWED/);
      assert.doesNotMatch(result.stderr, /DOCKER:(build|push)/);
    });
  }
  test(`${action}: registry/inspect errors, missing platform and wrong OS fail closed`, () => {
    for (const options of [
      { pullStatus: 1 }, { inspectStatus: 1 }, { platform: '' }, { platform: 'windows/amd64' },
    ]) {
      const result = check({ action, target: 'cohost-amd64', platform: 'linux/amd64', ...options });
      assert.notEqual(result.status, 0);
      assert.doesNotMatch(result.stdout, /HOST_DEPLOY_ALLOWED/);
      if (options.pullStatus) assert.doesNotMatch(result.stderr, /DOCKER:image inspect/);
    }
  });
}
