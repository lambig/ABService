import copy
import datetime as dt
import io
import json
import unittest
from unittest.mock import Mock

import monitor


NOW = dt.datetime(2025, 1, 2, tzinfo=dt.timezone.utc)
KEY = 'cohost/runs/example/manifest.json'


class MonitorTest(unittest.TestCase):
    def setUp(self):
        self.s3 = Mock()
        self.cloudwatch = Mock()
        self.config = {'bucket': 'example-backups', 'prefix': 'cohost', 'name': 'example', 'max_age_hours': 18}
        self.manifest = {
            'format': 1, 'snapshotAt': (NOW - dt.timedelta(hours=12)).isoformat(), 'completedAt': NOW.isoformat(),
            'files': {name: {'key': 'cohost/runs/example/' + name, 'versionId': 'fixture-version',
                             'bytes': 10, 'sha256': 'a' * 64} for name in ('db.dump', 'assets.json')}}
        self.s3.get_paginator.return_value.paginate.return_value = [
            {'Contents': [{'Key': 'cohost/runs/incomplete/db.dump', 'LastModified': NOW}]},
            {'Contents': [{'Key': KEY, 'LastModified': NOW}]}]
        self.s3.head_object.return_value = {'ContentLength': 10}
        self.s3.get_object.side_effect = lambda **kw: {
            'Body': io.BytesIO(json.dumps(self.manifest).encode()), 'VersionId': 'fixture-manifest'}

    def run_check(self):
        return monitor.publish(self.s3, self.cloudwatch, self.config, NOW)

    def metric(self):
        return self.cloudwatch.put_metric_data.call_args.kwargs['MetricData'][0]['Value']

    def test_pagination_skips_incomplete_run_and_checks_exact_versions(self):
        self.assertTrue(self.run_check()['healthy'])
        self.assertEqual(self.metric(), 0)
        self.s3.get_object.assert_called_once_with(Bucket='example-backups', Key=KEY)
        self.assertEqual(self.s3.head_object.call_count, 2)
        for call in self.s3.head_object.call_args_list:
            self.assertEqual(call.kwargs['VersionId'], 'fixture-version')

    def test_new_upload_does_not_refresh_old_snapshot(self):
        self.manifest['snapshotAt'] = (NOW - dt.timedelta(hours=19)).isoformat()
        self.assertEqual(self.run_check()['reason'], 'stale')
        self.assertEqual(self.metric(), 1)

    def test_empty_bucket_is_unhealthy(self):
        self.s3.get_paginator.return_value.paginate.return_value = [{}]
        self.assertEqual(self.run_check()['reason'], 'missing')
        self.assertEqual(self.metric(), 1)

    def test_invalid_markers_never_report_healthy(self):
        mutations = [
            lambda m: m.update(format=2),
            lambda m: m.update(snapshotAt='not-a-date'),
            lambda m: m.update(snapshotAt=(NOW + dt.timedelta(hours=1)).isoformat()),
            lambda m: m.update(snapshotAt='2025-01-01T12:00:00'),
            lambda m: m['files'].pop('db.dump'),
            lambda m: m['files']['db.dump'].update(key='elsewhere/db.dump'),
            lambda m: m['files']['db.dump'].update(versionId='null'),
            lambda m: m['files']['db.dump'].update(sha256='broken'),
            lambda m: m['files']['db.dump'].update(bytes=0),
        ]
        original = copy.deepcopy(self.manifest)
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                self.manifest = copy.deepcopy(original)
                mutation(self.manifest)
                self.assertEqual(self.run_check()['reason'], 'unreadable')
                self.assertEqual(self.metric(), 1)

    def test_missing_version_and_size_mismatch_are_unhealthy(self):
        self.s3.head_object.side_effect = RuntimeError('private SDK response')
        self.assertFalse(self.run_check()['healthy'])
        self.s3.head_object.side_effect = None
        self.s3.head_object.return_value = {'ContentLength': 9}
        self.assertFalse(self.run_check()['healthy'])

    def test_read_failure_never_emits_success_or_sensitive_error(self):
        from contextlib import redirect_stdout
        self.s3.get_object.side_effect = RuntimeError('SECRET sentinel')
        output = io.StringIO()
        with redirect_stdout(output):
            self.assertFalse(self.run_check()['healthy'])
        self.assertNotIn('SECRET', output.getvalue())
        self.assertEqual(self.metric(), 1)

    def test_metric_failure_is_not_reported_as_success(self):
        self.cloudwatch.put_metric_data.side_effect = RuntimeError('SECRET sentinel')
        with self.assertRaisesRegex(RuntimeError, '^Backup health metric could not be published$'):
            self.run_check()


if __name__ == '__main__':
    unittest.main()
