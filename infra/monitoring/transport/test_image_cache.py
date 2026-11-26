"""Image cache contract without a daemon, network or elevated privileges."""

import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import tarfile
import unittest
from unittest.mock import Mock, patch

import image_cache
import test_transport as transport


class ImageCacheTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.lock = json.loads(image_cache.LOCK.read_text())
        self.config = json.dumps({'os': 'linux', 'architecture': 'amd64',
                                  'rootfs': {'type': 'layers', 'diff_ids': []}}).encode()
        self.export_config = self.config
        self.lock['config'] = 'sha256:' + hashlib.sha256(self.config).hexdigest()
        lock_patch = patch.object(image_cache, 'LOCK')
        self.lock_file = lock_patch.start()
        self.addCleanup(lock_patch.stop)
        self.lock_file.read_text.return_value = json.dumps(self.lock)
        self.image = self.lock['image']
        self.image_id = self.lock['config']
        self.archive = Path(self.directory.name) / (self.image_id.split(':')[1] + '.tar')
        self.inspected = {'Id': self.image_id, 'Os': 'linux', 'Architecture': 'amd64'}
        self.run = Mock(side_effect=self.command)
        self.pull = Mock()
        self.load_output = 'Loaded image ID: ' + self.image_id
        self.manifest = [{'Config': 'config.json', 'RepoTags': [], 'Layers': []}]

    def command(self, *args, **kwargs):
        if args[1] == 'info':
            return 'linux/x86_64'
        if args[1:3] == ('image', 'inspect'):
            return json.dumps([self.inspected])
        if args[1:3] == ('image', 'load'):
            return self.load_output
        if args[1:3] == ('image', 'save'):
            with tarfile.open(args[4], 'w') as saved:
                for name, contents in (('manifest.json', json.dumps(self.manifest).encode()),
                                       ('config.json', self.export_config)):
                    member = tarfile.TarInfo(name)
                    member.size = len(contents)
                    saved.addfile(member, io.BytesIO(contents))
            Path(args[4]).chmod(0o600)  # Docker save creates a root-only archive.
        return ''

    def acquire(self):
        return image_cache.cached_image(self.image, self.directory.name, self.run, self.pull)

    def test_miss_pulls_verifies_and_atomically_saves_only_the_image(self):
        self.assertEqual(self.acquire(), self.image_id)
        self.pull.assert_called_once_with(self.image)
        with tarfile.open(self.archive) as saved:
            self.assertEqual(saved.extractfile('config.json').read(), self.config)
        self.assertEqual(self.archive.stat().st_mode & 0o777, 0o644)
        self.assertEqual(self.archive.parent.stat().st_mode & 0o777, 0o700)
        self.run.assert_any_call('docker', 'image', 'inspect', self.image)
        self.assertFalse(self.archive.with_suffix('.partial').exists())

    def test_new_directory_is_readable_even_with_restrictive_umask(self):
        directory = Path(self.directory.name) / 'new-cache'
        previous_umask = os.umask(0o077)
        try:
            self.assertEqual(image_cache.cached_image(
                self.image, directory, self.run, self.pull), self.image_id)
        finally:
            os.umask(previous_umask)
        self.assertEqual(directory.stat().st_mode & 0o777, 0o755)
        self.assertEqual((directory / self.archive.name).stat().st_mode & 0o777, 0o644)

    def test_directory_symlinks_are_rejected_without_changing_the_target(self):
        target = Path(self.directory.name) / 'private'
        target.mkdir(mode=0o700)
        marker = target / 'private.txt'
        marker.write_bytes(b'private fixture')
        link = Path(self.directory.name) / 'linked'
        link.symlink_to(target, target_is_directory=True)
        dangling = Path(self.directory.name) / 'dangling'
        dangling.symlink_to(target / 'missing', target_is_directory=True)
        for directory in (link, link / 'child', dangling):
            with self.subTest(directory=directory), self.assertRaisesRegex(ValueError, 'symlink'):
                image_cache.cached_image(self.image, directory, self.run, self.pull)
        self.assertEqual(target.stat().st_mode & 0o777, 0o700)
        self.assertEqual(marker.read_bytes(), b'private fixture')
        self.assertEqual(list(target.iterdir()), [marker])
        self.pull.assert_not_called()
        self.assertFalse(any(call.args[1] == 'image' for call in self.run.call_args_list))

    def test_archive_symlink_is_rejected_without_loading_or_changing_target(self):
        target = Path(self.directory.name) / 'private.txt'
        target.write_bytes(b'private fixture')
        target.chmod(0o600)
        self.archive.symlink_to(target)
        with self.assertRaisesRegex(ValueError, 'symlink'):
            self.acquire()
        self.assertEqual(target.stat().st_mode & 0o777, 0o600)
        self.assertEqual(target.read_bytes(), b'private fixture')
        self.pull.assert_not_called()
        self.assertFalse(any(call.args[1] == 'image' for call in self.run.call_args_list))

    def test_save_does_not_follow_a_preexisting_partial_symlink(self):
        target = Path(self.directory.name) / 'private.txt'
        target.write_bytes(b'private fixture')
        target.chmod(0o600)
        self.archive.with_suffix('.partial').symlink_to(target)
        self.assertEqual(self.acquire(), self.image_id)
        self.assertEqual(target.stat().st_mode & 0o777, 0o600)
        self.assertEqual(target.read_bytes(), b'private fixture')

    def test_hit_loads_verified_content_without_registry_access(self):
        self.archive.write_bytes(b'fixture archive')
        self.assertEqual(self.acquire(), self.image_id)
        self.pull.assert_not_called()
        self.run.assert_any_call('docker', 'image', 'load', '--input', str(self.archive), timeout=120)
        self.run.assert_any_call('docker', 'image', 'inspect', self.image_id)
        self.assertEqual(self.archive.read_bytes(), b'fixture archive')
        self.assertEqual(list(self.archive.parent.glob('*.partial')), [])

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
        for field, value in (('Id', 'mutable:tag'), ('Os', 'windows'), ('Architecture', 'arm64')):
            with self.subTest(field=field):
                original = self.inspected[field]
                self.inspected[field] = value
                with self.assertRaises(ValueError):
                    self.acquire()
                self.assertFalse(self.archive.exists())
                self.inspected[field] = original

    def test_containerd_id_is_returned_only_after_verifying_config_content(self):
        runtime_id = 'sha256:' + 'a' * 64
        self.inspected['Id'] = runtime_id
        self.load_output = 'Loaded image ID: ' + runtime_id
        self.assertEqual(self.acquire(), runtime_id)
        self.run.reset_mock()
        self.pull.reset_mock()
        self.assertEqual(self.acquire(), runtime_id)
        self.pull.assert_not_called()
        self.run.assert_any_call('docker', 'image', 'inspect', runtime_id)
        saves = [call for call in self.run.call_args_list if call.args[1:3] == ('image', 'save')]
        self.assertEqual(saves[0].args[-1], runtime_id)

    def test_loaded_tag_is_resolved_to_immutable_id_before_export_and_startup(self):
        self.archive.write_bytes(b'fixture archive')
        self.load_output = 'Loaded image: fixture.example.test/monitor:cached'
        self.assertEqual(self.acquire(), self.image_id)
        self.run.assert_any_call('docker', 'image', 'inspect', 'fixture.example.test/monitor:cached')
        saves = [call for call in self.run.call_args_list if call.args[1:3] == ('image', 'save')]
        self.assertEqual(saves[0].args[-1], self.image_id)

    def test_empty_or_multiple_load_results_fail_without_inspect_or_pull(self):
        self.archive.write_bytes(b'fixture archive')
        for output in ('', 'progress only', 'Loaded image ID: sha256:a\nLoaded image ID: sha256:b'):
            with self.subTest(output=output):
                self.load_output = output
                self.run.reset_mock()
                with self.assertRaises(ValueError):
                    self.acquire()
                self.assertFalse(any(call.args[1:3] == ('image', 'inspect') for call in self.run.call_args_list))
        self.pull.assert_not_called()

    def test_wrong_config_is_rejected_on_both_hit_and_miss_without_replacing_cache(self):
        self.inspected['Id'] = 'sha256:' + 'a' * 64
        self.export_config = self.config + b' '
        for hit in (False, True):
            with self.subTest(hit=hit):
                if hit:
                    self.archive.write_bytes(b'existing fixture')
                with self.assertRaisesRegex(ValueError, 'pinned content'):
                    self.acquire()
                self.assertEqual(self.archive.read_bytes() if hit else self.archive.exists(),
                                 b'existing fixture' if hit else False)
                self.assertEqual(list(self.archive.parent.glob('*.partial')), [])

    def test_archive_requires_one_image_and_bounded_regular_config(self):
        for manifest in ([], self.manifest * 2, [{'Config': 'missing'}]):
            with self.subTest(manifest=manifest):
                self.manifest = manifest
                with self.assertRaises((ValueError, KeyError)):
                    self.acquire()
                self.assertFalse(self.archive.exists())
        self.manifest = [{'Config': 'config.json'}]
        self.export_config = b'x' * (1024 * 1024 + 1)
        with self.assertRaisesRegex(ValueError, 'metadata'):
            self.acquire()

    def test_matching_hash_with_wrong_config_platform_is_still_rejected(self):
        self.export_config = json.dumps({'os': 'linux', 'architecture': 'arm64'}).encode()
        self.lock['config'] = 'sha256:' + hashlib.sha256(self.export_config).hexdigest()
        self.lock_file.read_text.return_value = json.dumps(self.lock)
        with self.assertRaisesRegex(ValueError, 'pinned platform'):
            self.acquire()

    def test_nonregular_export_metadata_is_rejected_without_following_links(self):
        def save_with_link(*args, **kwargs):
            result = self.command(*args, **kwargs)
            if args[1:3] == ('image', 'save'):
                with tarfile.open(args[4], 'a') as saved:
                    member = tarfile.TarInfo('config.json')
                    member.type = tarfile.SYMTYPE
                    member.linkname = '/private-fixture'
                    saved.addfile(member)
            return result

        self.run.side_effect = save_with_link
        with self.assertRaisesRegex(ValueError, 'metadata'):
            self.acquire()
        self.assertFalse(self.archive.exists())

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
        self.export_config = b'wrong content'
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
        runtime_id = 'sha256:' + 'a' * 64
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
                name: {'image': runtime_id} for name in ('translate', 'agent')})
            raise error

        with patch.object(transport, 'cached_image', return_value=runtime_id) as acquire, \
                patch.object(transport, 'run', side_effect=compose):
            with self.assertRaises(subprocess.CalledProcessError) as raised:
                transport.start_transport(['docker', 'compose'], {}, self.directory.name)
        self.assertIs(raised.exception, error)
        acquire.assert_called_once()


if __name__ == '__main__':
    unittest.main()
