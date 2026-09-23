"""Linux fault tests; every credential below is deliberately invalid synthetic data."""

from datetime import datetime, timedelta, timezone
import importlib.util
import json
import os
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


SOURCE = Path(__file__).with_name("refresh.py")
spec = importlib.util.spec_from_file_location("refresh", SOURCE)
refresh = importlib.util.module_from_spec(spec)
spec.loader.exec_module(refresh)


def response(seconds=900):
    return {"Version": 1, "AccessKeyId": "INVALID_TEST_ACCESS_KEY",
            "SecretAccessKey": "INVALID_TEST_SECRET", "SessionToken": "INVALID_TEST_TOKEN",
            "Expiration": (datetime.now(timezone.utc) + timedelta(seconds=seconds)).isoformat()}


class RefreshTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.runtime = self.root / "runtime"
        self.runtime.mkdir(mode=0o700)
        self.generation = self.root / "v1"
        self.generation.mkdir(mode=0o700)
        (self.generation / "certificate.pem").write_text("not a real certificate")
        (self.generation / "private-key.pem").write_text("not a real key")
        (self.generation / "private-key.pem").chmod(0o600)
        self.current = self.root / "current"
        self.current.symlink_to(self.generation)
        self.helper = self.root / "helper"
        self.helper.write_text(
            "#!/usr/bin/python3\nimport json, os, pathlib, sys\n"
            "root = pathlib.Path(__file__).parent\n"
            "(root / 'args.json').write_text(json.dumps(sys.argv[1:]))\n"
            "(root / 'env.json').write_text(json.dumps(dict(os.environ)))\n"
            "if (root / 'fail').exists():\n"
            "    print('INVALID_TEST_SECRET', file=sys.stderr)\n"
            "    print('INVALID_TEST_TOKEN')\n"
            "    sys.exit(1)\n"
            "sys.stdout.write((root / 'response.json').read_text())\n")
        self.helper.chmod(0o700)
        self.config = self.root / "config.json"
        self.values = {"helper": str(self.helper), "certificate_directory": str(self.current),
                       "trust_anchor_arn": "invalid-test-anchor", "profile_arn": "invalid-test-profile",
                       "role_arn": "invalid-test-role"}
        self.config.write_text(json.dumps(self.values))
        self.payload(response())

    def payload(self, value):
        (self.root / "response.json").write_text(json.dumps(value))

    def run_refresh(self):
        refresh.refresh(self.config, self.runtime)

    def status(self):
        return json.loads((self.runtime / "status.json").read_text())

    def cli(self):
        return subprocess.run([sys.executable, str(SOURCE), "--config", str(self.config),
                               "--runtime-directory", str(self.runtime)], capture_output=True, text=True, timeout=10)

    def test_publish_permissions_and_no_secret_status_or_console(self):
        result = self.cli()
        self.assertEqual(0, result.returncode, result.stderr)
        content = (self.runtime / "credentials").read_text()
        self.assertIn("[monitor]\n", content)
        self.assertIn("aws_session_token = INVALID_TEST_TOKEN\n", content)
        self.assertEqual("success", self.status()["result"])
        for path in self.runtime.iterdir():
            self.assertEqual(0o600, stat.S_IMODE(path.stat().st_mode))
        self.assertEqual([], list(self.runtime.glob(".refresh-*")))
        visible = result.stdout + result.stderr + (self.runtime / "status.json").read_text()
        self.assertNotIn("INVALID_TEST", visible)

    def test_failed_helper_preserves_good_file_and_last_success(self):
        self.run_refresh()
        original = (self.runtime / "credentials").read_bytes()
        success = self.status()["last_success_at"]
        (self.root / "fail").touch()
        result = self.cli()
        self.assertEqual(1, result.returncode)
        self.assertNotIn("INVALID_TEST", result.stdout + result.stderr)
        self.assertEqual(original, (self.runtime / "credentials").read_bytes())
        self.assertEqual(success, self.status()["last_success_at"])
        self.assertEqual("helper_failed", self.status()["code"])
        self.assertEqual("acquire", self.status()["stage"])

    def test_bad_responses_preserve_credentials(self):
        self.run_refresh()
        original = (self.runtime / "credentials").read_bytes()
        cases = [None, [], {**response(), "Version": True}, response(-1), response(770),
                 response(7200), {**response(), "Expiration": "no-date"},
                 {**response(), "Expiration": "2030-01-01T00:00:00"},
                 {**response(), "SessionToken": "token\n[admin]"},
                 {**response(), "SecretAccessKey": None}, {"Version": 1}]
        for value in cases:
            with self.subTest(value=value):
                self.payload(value)
                with self.assertRaises(refresh.RefreshError):
                    self.run_refresh()
                self.assertEqual(original, (self.runtime / "credentials").read_bytes())
                self.assertEqual("validate", self.status()["stage"])
        (self.root / "response.json").write_text("malformed INVALID_TEST_SECRET")
        with self.assertRaises(refresh.RefreshError):
            self.run_refresh()
        self.assertEqual(original, (self.runtime / "credentials").read_bytes())

    def test_opaque_credentials_larger_than_8192_are_published_unchanged(self):
        data = response()
        for field in ("AccessKeyId", "SecretAccessKey", "SessionToken"):
            data[field] = "INVALID_TEST:" + "x" * 9000 + "@,{}[]!é"
        self.payload(data)
        self.run_refresh()
        content = (self.runtime / "credentials").read_text()
        for field, name in (("AccessKeyId", "aws_access_key_id"),
                            ("SecretAccessKey", "aws_secret_access_key"),
                            ("SessionToken", "aws_session_token")):
            self.assertIn(f"{name} = {data[field]}\n", content)
        self.assertEqual("success", self.status()["result"])

    def test_empty_and_line_breaking_values_are_rejected_in_each_field(self):
        self.run_refresh()
        original = (self.runtime / "credentials").read_bytes()
        for field in ("AccessKeyId", "SecretAccessKey", "SessionToken"):
            for value in ("", "token\r[admin]", "token\n[admin]", "token\0suffix"):
                with self.subTest(field=field, value=value):
                    self.payload({**response(), field: value})
                    with self.assertRaisesRegex(refresh.RefreshError, "invalid_helper_response"):
                        self.run_refresh()
                    self.assertEqual(original, (self.runtime / "credentials").read_bytes())

    def test_response_byte_budget_accepts_exact_boundary_and_rejects_next_byte(self):
        raw = json.dumps(response()).encode()
        boundary = raw + b" " * (refresh.MAX_RESPONSE_BYTES - len(raw))
        (self.root / "response.json").write_bytes(boundary)
        self.run_refresh()
        original = (self.runtime / "credentials").read_bytes()
        (self.root / "response.json").write_bytes(boundary + b" ")
        with self.assertRaisesRegex(refresh.RefreshError, "helper_output_too_large"):
            self.run_refresh()
        self.assertEqual(original, (self.runtime / "credentials").read_bytes())
        self.assertEqual("acquire", self.status()["stage"])

    def test_endless_output_is_stopped_and_reaped_without_leaking_or_replacing_credentials(self):
        self.run_refresh()
        original = (self.runtime / "credentials").read_bytes()
        self.helper.write_text(
            "#!/usr/bin/python3\nimport os, pathlib, sys\n"
            "pathlib.Path(__file__).with_name('pid').write_text(str(os.getpid()))\n"
            "print('INVALID_TEST_SECRET', file=sys.stderr)\n"
            "while True:\n    os.write(1, b'INVALID_TEST_TOKEN' * 1024)\n")
        result = self.cli()
        self.assertEqual(1, result.returncode)
        self.assertIn("helper_output_too_large", result.stderr)
        self.assertNotIn("INVALID_TEST", result.stdout + result.stderr)
        self.assertEqual(original, (self.runtime / "credentials").read_bytes())
        self.assertEqual("helper_output_too_large", self.status()["code"])
        pid = int((self.root / "pid").read_text())
        with self.assertRaises(ProcessLookupError):
            os.kill(pid, 0)

    def test_timeout_covers_partial_output_and_wait_after_stdout_closes(self):
        self.run_refresh()
        original = (self.runtime / "credentials").read_bytes()
        for action in ("os.write(1, b'{')", "os.close(1)"):
            with self.subTest(action=action):
                self.helper.write_text("#!/usr/bin/python3\nimport os, time\n" + action + "\ntime.sleep(10)\n")
                with patch.object(refresh, "HELPER_TIMEOUT_SECONDS", 0.2):
                    with self.assertRaisesRegex(refresh.RefreshError, "helper_timeout"):
                        self.run_refresh()
                self.assertEqual(original, (self.runtime / "credentials").read_bytes())

    def test_timeout_retains_good_file(self):
        self.run_refresh()
        original = (self.runtime / "credentials").read_bytes()
        self.helper.write_text("#!/usr/bin/python3\nimport time\ntime.sleep(10)\n")
        with patch.object(refresh, "HELPER_TIMEOUT_SECONDS", 0.05):
            with self.assertRaisesRegex(refresh.RefreshError, "helper_timeout"):
                self.run_refresh()
        self.assertEqual(original, (self.runtime / "credentials").read_bytes())
        self.assertEqual("helper_timeout", self.status()["code"])

    def test_lock_rejects_second_process_without_replacing_status(self):
        self.run_refresh()
        original = (self.runtime / "status.json").read_bytes()
        with refresh.exclusive(self.runtime):
            result = self.cli()
        self.assertEqual(1, result.returncode)
        self.assertIn("already_running", result.stderr)
        self.assertEqual(original, (self.runtime / "status.json").read_bytes())
        # A rejected second process must not unlink the inode still held by the first.
        self.run_refresh()

    def test_certificate_generation_is_pinned_during_symlink_switch(self):
        command = refresh.load_config(self.config)
        other = self.root / "v2"
        other.mkdir()
        self.current.unlink()
        self.current.symlink_to(other)
        for flag in ("--certificate", "--private-key"):
            self.assertEqual(self.generation, Path(command[command.index(flag) + 1]).parent)

    def test_helper_does_not_inherit_operator_credentials(self):
        with patch.dict(os.environ, {"AWS_SECRET_ACCESS_KEY": "INVALID_OPERATOR_SECRET",
                                     "AWS_PROFILE": "operator", "AWS_CONFIG_FILE": "/bad/config"}):
            self.run_refresh()
        environment = json.loads((self.root / "env.json").read_text())
        self.assertNotIn("AWS_SECRET_ACCESS_KEY", environment)
        self.assertNotIn("AWS_PROFILE", environment)
        self.assertNotIn("AWS_CONFIG_FILE", environment)

    def test_rename_failure_keeps_good_file_and_cleans_temporary(self):
        self.run_refresh()
        original = (self.runtime / "credentials").read_bytes()
        real_replace = os.replace

        def fail_credentials(source, target):
            if Path(target).name == "credentials":
                raise OSError("injected disk error INVALID_TEST_SECRET")
            return real_replace(source, target)

        with patch.object(refresh.os, "replace", side_effect=fail_credentials):
            with self.assertRaisesRegex(refresh.RefreshError, "refresh_failed"):
                self.run_refresh()
        self.assertEqual(original, (self.runtime / "credentials").read_bytes())
        self.assertEqual([], list(self.runtime.glob(".refresh-*")))
        self.assertEqual("publish", self.status()["stage"])

    def test_reader_with_open_old_inode_sees_complete_old_value(self):
        self.run_refresh()
        with (self.runtime / "credentials").open() as reader:
            old = reader.read()
            self.payload({**response(), "AccessKeyId": "INVALID_NEXT_KEY"})
            self.run_refresh()
            reader.seek(0)
            self.assertEqual(old, reader.read())
        self.assertIn("INVALID_NEXT_KEY", (self.runtime / "credentials").read_text())

    def test_failed_first_refresh_does_not_create_empty_credentials(self):
        (self.root / "fail").touch()
        with self.assertRaises(refresh.RefreshError):
            self.run_refresh()
        self.assertFalse((self.runtime / "credentials").exists())
        self.assertIsNone(self.status()["last_success_at"])

    def test_rejects_runtime_alias_and_loose_permissions(self):
        alias = self.root / "alias"
        alias.symlink_to(self.runtime)
        with self.assertRaisesRegex(refresh.RefreshError, "unsafe_runtime"):
            refresh.refresh(self.config, alias)
        self.runtime.chmod(0o755)
        with self.assertRaisesRegex(refresh.RefreshError, "unsafe_runtime"):
            self.run_refresh()

    def test_rejects_symlinked_lock_and_readable_private_key(self):
        sentinel = self.root / "sentinel"
        sentinel.write_text("untouched")
        (self.runtime / "refresh.lock").symlink_to(sentinel)
        self.assertEqual(1, self.cli().returncode)
        self.assertEqual("untouched", sentinel.read_text())
        (self.runtime / "refresh.lock").unlink()
        (self.generation / "private-key.pem").chmod(0o644)
        with self.assertRaisesRegex(refresh.RefreshError, "unsafe_private_key"):
            self.run_refresh()

    def test_abandoned_temporary_removed_under_lock(self):
        abandoned = self.runtime / ".refresh-abandoned"
        abandoned.write_text("INVALID_TEST_SECRET")
        self.run_refresh()
        self.assertFalse(abandoned.exists())

    def test_status_write_failure_is_not_reported_as_success(self):
        real_write = refresh.atomic_write

        def fail_status(directory, name, text):
            if name == "status.json":
                raise OSError("injected status write failure")
            return real_write(directory, name, text)

        with patch.object(refresh, "atomic_write", side_effect=fail_status):
            with self.assertRaises(OSError):
                self.run_refresh()
        self.assertTrue((self.runtime / "credentials").exists())
        self.assertFalse((self.runtime / "status.json").exists())


if __name__ == "__main__":
    unittest.main()
