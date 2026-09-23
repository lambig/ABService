import datetime as dt
import fcntl
import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import backup


CONFIG = {'region': 'us-east-1', 'bucket': 'invalid-fixture-backups', 'prefix': 'fixture'}


class BackupTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.root.chmod(0o700)

    def test_failed_attempt_retains_previous_success(self):
        path = self.root / 'backup-status.json'
        path.write_text(json.dumps({'lastSuccess': {'key': 'earlier-manifest'}}))
        with patch.object(backup, 'create_locked', side_effect=RuntimeError('invalid-secret-fixture')):
            with self.assertRaises(RuntimeError):
                backup.create(CONFIG, self.root)
        status = json.loads(path.read_text())
        self.assertEqual(status['outcome'], 'failed')
        self.assertEqual(status['lastSuccess']['key'], 'earlier-manifest')
        self.assertNotIn('invalid-secret-fixture', path.read_text())

    def test_deploy_lock_prevents_backup(self):
        with (self.root / 'deploy.lock').open('w') as held:
            fcntl.flock(held, fcntl.LOCK_EX | fcntl.LOCK_NB)
            with patch.object(backup, 'create_locked') as create:
                with self.assertRaises(BlockingIOError):
                    backup.create(CONFIG, self.root)
                create.assert_not_called()

    def test_preflight_rejects_asset_expiry_and_suspended_versioning(self):
        block = dict.fromkeys(('BlockPublicAcls', 'IgnorePublicAcls', 'BlockPublicPolicy', 'RestrictPublicBuckets'), True)
        def aws(config, op, *args):
            return {'get-bucket-versioning': {'Status': 'Enabled'},
                    'get-public-access-block': {'PublicAccessBlockConfiguration': block},
                    'get-bucket-lifecycle-configuration': {'Rules': [{'Status': 'Enabled', 'Filter': {'Prefix': 'assets/'}, 'Expiration': {'Days': 1}}]}}[op]
        with patch.object(backup, 'aws', side_effect=aws):
            with self.assertRaises(backup.BackupError):
                backup.preflight(CONFIG, 'invalid-fixture-assets')
        with patch.object(backup, 'aws', return_value={'Status': 'Suspended'}):
            with self.assertRaises(backup.BackupError):
                backup.preflight(CONFIG, 'invalid-fixture-assets')

    def test_put_requires_version_and_conditional_checksum_upload(self):
        file = self.root / 'fixture'
        file.write_bytes(b'fixture-data')
        with patch.object(backup, 'aws', return_value={'VersionId': 'v1'}) as aws:
            stored = backup.put(CONFIG, 'fixture/key', file)
        args = aws.call_args.args
        self.assertIn('--if-none-match', args)
        self.assertIn('--checksum-sha256', args)
        self.assertIn('AES256', args)
        self.assertEqual(stored['sha256'], hashlib.sha256(b'fixture-data').hexdigest())
        with patch.object(backup, 'aws', return_value={'VersionId': 'null'}):
            with self.assertRaises(backup.BackupError):
                backup.put(CONFIG, 'fixture/key', file)

    def test_streamed_checksum_across_chunk_boundaries(self):
        file = self.root / 'checksum-fixture'
        for data in (b'', b'x', b'y' * (2 * 1024 * 1024 + 17)):
            file.write_bytes(data)
            self.assertEqual(backup.checksum(file), hashlib.sha256(data).hexdigest())

    def test_fetch_detects_corruption_and_refuses_overwrite(self):
        prefix = 'fixture/runs/example'
        manifest = {'format': 1, 'snapshotAt': backup.now(), 'files': {
            name: {'key': prefix + '/' + name, 'versionId': 'saved-version', 'bytes': 4,
                   'sha256': hashlib.sha256(b'good').hexdigest()} for name in ('db.dump', 'assets.json')}}
        def download(config, operation, *args):
            path = Path(args[-1])
            if path.name == 'manifest.json':
                path.write_text(json.dumps(manifest))
                self.assertIn('manifest-version', args)
            else:
                path.write_bytes(b'evil')
                self.assertIn('saved-version', args)
            return {}
        with patch.object(backup, 'aws', side_effect=download):
            with self.assertRaises(backup.BackupError):
                backup.fetch(CONFIG, prefix + '/manifest.json', 'manifest-version', self.root / 'download')
        with self.assertRaises(FileExistsError):
            backup.fetch(CONFIG, prefix + '/manifest.json', 'manifest-version', self.root / 'download')

    def test_freshness_uses_snapshot_time_not_upload_time(self):
        old = (dt.datetime.now(dt.timezone.utc) - dt.timedelta(hours=25)).isoformat()
        def aws(config, op, *args):
            if op == 'list-objects-v2':
                return {'Contents': [{'Key': 'fixture/runs/one/manifest.json', 'LastModified': backup.now()}]}
            Path(args[-1]).write_text(json.dumps({'format': 1, 'snapshotAt': old, 'files': {'db.dump': {}, 'assets.json': {}}}))
            return {'VersionId': 'v1'}
        with patch.object(backup, 'aws', side_effect=aws):
            with self.assertRaises(backup.BackupError):
                backup.freshness(CONFIG, 24)


if __name__ == '__main__':
    unittest.main()
