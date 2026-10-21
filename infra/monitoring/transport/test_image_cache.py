"""Image cache contract without a daemon, network or elevated privileges."""

import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch

import image_cache
import test_transport as transport


class ImageCacheTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.lock = json.loads(image_cache.LOCK.read_text())
        self.image = self.lock['image']
        self.image_id = self.lock['config']
        self.archive = Path(self.directory.name) / (self.image_id.split(':')[1] + '.tar')
        self.inspected = {'Id': self.image_id, 'Os': 'linux', 'Architecture': 'amd64'}
        self.run = Mock(side_effect=self.command)
        self.pull = Mock()

    def command(self, *args, **kwargs):
        if args[1] == 'info':
            return 'linux/x86_64'
        if args[1:3] == ('image', 'inspect'):
            return json.dumps([self.inspected])
        if args[1:3] == ('image', 'save'):
            Path(args[4]).write_bytes(b'fixture archive')
            Path(args[4]).chmod(0o600)  # Docker save creates a root-only archive.
        return ''

    def acquire(self):
        return image_cache.cached_image(self.image, self.directory.name, self.run, self.pull)

    def test_miss_pulls_verifies_and_atomically_saves_only_the_image(self):
        self.assertEqual(self.acquire(), self.image_id)
        self.pull.assert_called_once_with(self.image)
        self.assertEqual(self.archive.read_bytes(), b'fixture archive')
        self.assertEqual(self.archive.stat().st_mode & 0o777, 0o644)
        self.assertEqual(self.archive.parent.stat().st_mode & 0o777, 0o755)
        self.run.assert_any_call('docker', 'image', 'inspect', self.image)
        self.assertFalse(self.archive.with_suffix('.partial').exists())

    def test_hit_loads_verified_content_without_registry_access(self):
        self.archive.write_bytes(b'fixture archive')
        self.assertEqual(self.acquire(), self.image_id)
        self.pull.assert_not_called()
        self.run.assert_any_call('docker', 'image', 'load', '--input', str(self.archive), timeout=120)
        self.run.assert_any_call('docker', 'image', 'inspect', self.image_id)
        self.assertFalse(any(call.args[1:3] == ('image', 'save') for call in self.run.call_args_list))

    def test_missing_cached_id_and_corrupt_archive_fail_without_pull(self):
        self.archive.write_bytes(b'corrupt fixture')
        for operation in ('load', 'inspect'):
            error = subprocess.CalledProcessError(1, ['docker', operation])

            def failing(*args, **kwargs):
                if args[1:3] == ('image', operation):
                    raise error
                return self.command(*args, **kwargs)

            self.run.side_effect = failing
            with self.subTest(operation=operation), self.assertRaises(subprocess.CalledProcessError):
                self.acquire()
        self.pull.assert_not_called()

    def test_wrong_id_or_platform_cannot_be_used_or_saved(self):
        for field, value in (('Id', 'sha256:' + '0' * 64), ('Os', 'windows'), ('Architecture', 'arm64')):
            with self.subTest(field=field):
                original = self.inspected[field]
                self.inspected[field] = value
                with self.assertRaises(ValueError):
                    self.acquire()
                self.assertFalse(self.archive.exists())
                self.inspected[field] = original

    def test_changed_compose_image_requires_a_lock_update_before_any_docker_call(self):
        self.image = 'registry.example.test/other@sha256:' + 'b' * 64
        with self.assertRaises(ValueError):
            self.acquire()
        self.run.assert_not_called()
        self.pull.assert_not_called()

    def test_unsupported_daemon_is_rejected_before_load_or_pull(self):
        self.run.side_effect = None
        self.run.return_value = 'linux/aarch64'
        with self.assertRaises(ValueError):
            self.acquire()
        self.pull.assert_not_called()
        self.assertEqual(self.run.call_count, 1)

    def test_pull_failure_propagates_and_does_not_create_cache(self):
        error = subprocess.CalledProcessError(1, ['docker', 'pull'])
        self.pull.side_effect = error
        with self.assertRaises(subprocess.CalledProcessError) as raised:
            self.acquire()
        self.assertIs(raised.exception, error)
        self.assertFalse(self.archive.exists())

    def test_only_exhausted_throttling_uses_the_identical_official_manifest(self):
        for stderr in ('toomanyrequests: Data limit exceeded', b'TOOMANYREQUESTS: Rate exceeded'):
            with self.subTest(stderr=stderr):
                self.archive.unlink(missing_ok=True)
                self.pull.reset_mock()
                self.pull.side_effect = [subprocess.CalledProcessError(1, ['docker'], stderr=stderr), None]
                self.assertEqual(self.acquire(), self.image_id)
                fallback = 'docker.io/amazon/cloudwatch-agent@' + self.lock['manifest']
                self.assertEqual([call.args for call in self.pull.call_args_list], [(self.image,), (fallback,)])
                self.run.assert_any_call('docker', 'image', 'inspect', fallback)

    def test_auth_missing_image_timeout_and_daemon_failure_never_use_fallback(self):
        for error in (subprocess.CalledProcessError(1, ['docker'], stderr='unauthorized'),
                      subprocess.CalledProcessError(1, ['docker'], stderr='manifest unknown'),
                      subprocess.TimeoutExpired(['docker'], 90), OSError('missing daemon')):
            with self.subTest(error=error):
                self.pull.reset_mock()
                self.pull.side_effect = error
                with self.assertRaises(type(error)):
                    self.acquire()
                self.pull.assert_called_once_with(self.image)
                self.assertFalse(self.archive.exists())

    def test_fallback_failure_or_wrong_content_remains_fatal(self):
        throttle = subprocess.CalledProcessError(1, ['docker'], stderr='toomanyrequests: Rate exceeded')
        final = subprocess.CalledProcessError(1, ['docker'], stderr='manifest unknown')
        self.pull.side_effect = [throttle, final]
        with self.assertRaises(subprocess.CalledProcessError) as raised:
            self.acquire()
        self.assertIs(raised.exception, final)
        self.assertFalse(self.archive.exists())
        self.pull.side_effect = [throttle, None]
        self.inspected['Id'] = 'sha256:' + '0' * 64
        with self.assertRaises(ValueError):
            self.acquire()
        self.assertFalse(self.archive.exists())

    def test_interrupted_save_does_not_leave_a_restorable_archive(self):
        def failing(*args, **kwargs):
            result = self.command(*args, **kwargs)
            if args[1:3] == ('image', 'save'):
                raise subprocess.TimeoutExpired(args, 120)
            return result

        self.run.side_effect = failing
        with self.assertRaises(subprocess.TimeoutExpired):
            self.acquire()
        self.assertEqual(list(Path(self.directory.name).iterdir()), [])

    def test_compose_uses_verified_ids_and_keeps_startup_failure_fatal(self):
        config = {'services': {name: {'image': self.image} for name in ('translate', 'agent')}}
        error = subprocess.CalledProcessError(1, ['docker', 'compose', 'up'])

        def compose(*args, **kwargs):
            if args[-1] == '--images':
                return self.image + '\n' + self.image
            if args[-1] == 'json':
                return json.dumps(config)
            self.assertEqual(args[-4:], ('up', '-d', '--pull', 'never'))
            override = json.loads(Path(args[-5]).read_text())
            self.assertEqual(override['services'], {
                name: {'image': self.image_id} for name in ('translate', 'agent')})
            raise error

        with patch.object(transport, 'cached_image', return_value=self.image_id) as acquire, \
                patch.object(transport, 'run', side_effect=compose):
            with self.assertRaises(subprocess.CalledProcessError) as raised:
                transport.start_transport(['docker', 'compose'], {}, self.directory.name)
        self.assertIs(raised.exception, error)
        acquire.assert_called_once()


if __name__ == '__main__':
    unittest.main()
