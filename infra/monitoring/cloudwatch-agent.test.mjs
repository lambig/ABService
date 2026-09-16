import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

// The agent config and the alarms in monitoring.tf must agree on namespace, metric names and the
// dimension the alarms filter by. A mismatch publishes metrics no alarm reads, silently.
const config = JSON.parse(readFileSync(new URL('./cloudwatch-agent.json', import.meta.url), 'utf8'));
const monitoring = readFileSync(new URL('../monitoring.tf', import.meta.url), 'utf8');

test('the agent sends only the host metrics the standard namespaces lack, tagged by instance', () => {
  assert.deepEqual(Object.keys(config.metrics.metrics_collected).sort(), ['disk', 'mem']);
  assert.deepEqual(config.metrics.metrics_collected.disk.resources, ['/']);
  assert.deepEqual(config.metrics.metrics_collected.disk.measurement, ['disk_used_percent']);
  assert.deepEqual(config.metrics.metrics_collected.mem.measurement, ['mem_used_percent']);
  assert.deepEqual(config.metrics.append_dimensions, { InstanceId: '${aws:InstanceId}' });
  // Alarms filter by InstanceId alone; without this aggregation every disk metric also carries path/fstype.
  assert.deepEqual(config.metrics.aggregation_dimensions, [['InstanceId']]);
  assert.equal(config.agent.run_as_user, 'cwagent');
});

test('every host metric the agent publishes has an alarm, in the same namespace and dimension', () => {
  assert.match(monitoring, new RegExp(`host_metrics_namespace\\s*=\\s*"${config.metrics.namespace}"`));
  for (const name of ['disk_used_percent', 'mem_used_percent']) {
    const alarm = monitoring.match(new RegExp(`metric_name\\s*=\\s*"${name}"[\\s\\S]{0,200}?dimensions\\s*=\\s*\\{ InstanceId = aws_instance\\.backend\\.id \\}`));
    assert.ok(alarm, `${name} needs an alarm keyed by the instance`);
  }
});
