"""Failure diagnostics without Docker, root, systemd or network operations."""

import contextlib
import io
import subprocess
import unittest
from unittest.mock import patch

import test_transport as transport


class DiagnosticsTests(unittest.TestCase):
    def test_command_failure_retains_output_and_original_exception(self):
        error = subprocess.CalledProcessError(17, ['docker', 'compose', 'up'],
                                             output='startup output', stderr='registry unavailable')
        output = io.StringIO()
        with patch.object(transport.subprocess, 'run', side_effect=error), contextlib.redirect_stdout(output):
            with self.assertRaises(subprocess.CalledProcessError) as raised:
                transport.run('docker', 'compose', 'up')
        self.assertIs(raised.exception, error)
        self.assertIn('startup output', output.getvalue())
        self.assertIn('registry unavailable', output.getvalue())

    def test_timeout_bytes_are_decoded_and_fixture_credentials_redacted(self):
        error = subprocess.TimeoutExpired(['docker'], 300, output=b'partial output',
                                          stderr=b'INVALID_TRANSPORT_TEST INVALID_TEST_SECRET INVALID_TEST_TOKEN')
        output = io.StringIO()
        with patch.object(transport.subprocess, 'run', side_effect=error), contextlib.redirect_stdout(output):
            with self.assertRaises(subprocess.TimeoutExpired) as raised:
                transport.run('docker')
        self.assertIs(raised.exception, error)
        self.assertIn('partial output', output.getvalue())
        self.assertNotIn('INVALID_', output.getvalue())

    def test_diagnostics_are_bounded_and_continue_after_collection_failure(self):
        result = subprocess.CompletedProcess(['docker'], 1, stdout='x' * 20000,
                                             stderr='INVALID_TEST_SECRET failed to collect logs')
        for error in (OSError('missing docker'), subprocess.TimeoutExpired(['docker'], 10)):
            output = io.StringIO()
            with patch.object(transport.subprocess, 'run', side_effect=[error, result]) as run, \
                    contextlib.redirect_stdout(output):
                transport.diagnose_compose(['docker', 'compose'], {})
            self.assertEqual(run.call_count, 2)
            self.assertTrue(all(call.kwargs['timeout'] == 10 for call in run.call_args_list))
            self.assertLess(len(output.getvalue()), 17000)
            self.assertIn('failed to collect logs', output.getvalue())
            self.assertNotIn('INVALID_TEST_SECRET', output.getvalue())


class ImageAcquisitionTests(unittest.TestCase):
    IMAGE = 'registry.example.test/agent@sha256:' + 'a' * 64

    def test_shared_image_is_pulled_once_before_startup_without_pulling(self):
        compose = ['docker', 'compose', '-f', 'fixture.yml']
        env = {'FIXTURE': 'true'}
        with patch.object(transport, 'run', side_effect=[f'{self.IMAGE}\n{self.IMAGE}', '', '']) as run:
            transport.start_transport(compose, env)
        self.assertEqual([call.args for call in run.call_args_list], [
            (*compose, 'config', '--images'), ('docker', 'pull', self.IMAGE),
            (*compose, 'up', '-d', '--pull', 'never')])
        self.assertEqual(run.call_args_list[1].kwargs, {'timeout': 90})
        self.assertEqual(run.call_args_list[2].kwargs, {'env': env})

    def test_registry_throttles_retry_with_bounded_backoff(self):
        for stderr in (b'toomanyrequests: Rate exceeded',
                       'Error response from daemon: toomanyrequests: Data limit exceeded'):
            error = subprocess.CalledProcessError(1, ['docker', 'pull'], stderr=stderr)
            with self.subTest(stderr=stderr), \
                    patch.object(transport, 'run', side_effect=[error, error, error, '']) as run, \
                    patch.object(transport.time, 'sleep') as sleep, contextlib.redirect_stdout(io.StringIO()):
                transport.pull_image(self.IMAGE)
            self.assertEqual(run.call_count, 4)
            self.assertTrue(all(call.kwargs == {'timeout': 90} for call in run.call_args_list))
            self.assertEqual([call.args for call in sleep.call_args_list], [(5,), (15,), (30,)])

    def test_exhausted_throttle_retains_final_failure_and_does_not_start(self):
        error = subprocess.CalledProcessError(1, ['docker', 'pull'], stderr='toomanyrequests: Rate exceeded')
        with patch.object(transport, 'run', side_effect=[self.IMAGE, error, error, error, error]) as run, \
                patch.object(transport.time, 'sleep') as sleep, contextlib.redirect_stdout(io.StringIO()):
            with self.assertRaises(subprocess.CalledProcessError) as raised:
                transport.start_transport(['docker', 'compose'], {})
        self.assertIs(raised.exception, error)
        self.assertEqual(run.call_count, 5)
        self.assertEqual(sleep.call_count, 3)
        self.assertTrue(all('up' not in call.args for call in run.call_args_list))

    def test_other_pull_failures_are_not_retried(self):
        for error in (
                subprocess.CalledProcessError(1, ['docker', 'pull'], stderr='unauthorized'),
                subprocess.CalledProcessError(1, ['docker', 'pull'], stderr='manifest unknown'),
                subprocess.CalledProcessError(1, ['docker', 'pull'], stderr='too many requests without the error code'),
                subprocess.TimeoutExpired(['docker', 'pull'], 90), OSError('missing docker')):
            with self.subTest(error=error), patch.object(transport, 'run', side_effect=error) as run, \
                    patch.object(transport.time, 'sleep') as sleep:
                with self.assertRaises(type(error)) as raised:
                    transport.pull_image(self.IMAGE)
                self.assertIs(raised.exception, error)
                run.assert_called_once()
                sleep.assert_not_called()

    def test_startup_failure_is_not_retried_even_if_it_mentions_throttling(self):
        error = subprocess.CalledProcessError(1, ['docker', 'compose', 'up'],
                                             stderr='toomanyrequests: Rate exceeded')
        with patch.object(transport, 'run', side_effect=[self.IMAGE, '', error]) as run, \
                patch.object(transport.time, 'sleep') as sleep:
            with self.assertRaises(subprocess.CalledProcessError) as raised:
                transport.start_transport(['docker', 'compose'], {})
        self.assertIs(raised.exception, error)
        self.assertEqual(run.call_count, 3)
        sleep.assert_not_called()

    def test_missing_or_unpinned_image_is_rejected_before_pull(self):
        for images in ('', 'registry.example.test/agent:latest'):
            with self.subTest(images=images), patch.object(transport, 'run', return_value=images) as run:
                with self.assertRaises(ValueError):
                    transport.start_transport(['docker', 'compose'], {})
                run.assert_called_once()


if __name__ == '__main__':
    unittest.main()
