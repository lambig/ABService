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


if __name__ == '__main__':
    unittest.main()
