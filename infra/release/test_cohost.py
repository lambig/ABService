import base64
import contextlib
import io
import json
import shlex
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import cohost

NODE = "mi-" + "0" * 17
COMMAND_ID = "00000000-0000-0000-0000-000000000000"
IMAGE = "000000000000.dkr.ecr.us-east-1.amazonaws.com/fixture@sha256:" + "b" * 64


class Transport(unittest.TestCase):
    def test_only_candidate_git_objects_are_transferred(self):
        calls = []
        def git(args, **kwargs):
            calls.append(args)
            return SimpleNamespace(returncode=0, stdout=b"# source from committed git object\n")
        with patch.object(cohost.subprocess, "run", side_effect=git):
            params = cohost.parameters(Path("candidate"), "a" * 40, IMAGE)
        self.assertEqual(len(calls), 2)
        self.assertTrue(all(args[:4] == ["git", "-C", "candidate", "show"] for args in calls))
        payload = json.loads(base64.b64decode(params["commands"][0].split()[-1]))
        self.assertEqual(payload["source"], "a" * 40)
        self.assertEqual(set(payload["files"]), {"deploy.py", "init-db.sh"})
        self.assertEqual(params["executionTimeout"], [str(cohost.EXECUTION_TIMEOUT)])
        self.assertIn("timeout --kill-after=10s 630s", params["commands"][0])
        self.assertGreater(cohost.POLL_SECONDS, cohost.DELIVERY_TIMEOUT + cohost.EXECUTION_TIMEOUT)
        argv = shlex.split(params["commands"][0])
        namespace = {"__name__": "transport_test"}
        exec(argv[argv.index("-c") + 1], namespace)
        self.assertEqual(set(namespace["validate"](payload)), {"deploy.py", "init-db.sh"})

    def test_invalid_source_image_missing_files_and_oversized_payload(self):
        for source, image in (("main", IMAGE), ("a" * 40, "repo:latest")):
            with self.assertRaises(ValueError):
                cohost.parameters(Path("."), source, image)
        for result in (SimpleNamespace(returncode=1, stdout=b""), SimpleNamespace(returncode=0, stdout=b"a" * 100000)):
            with patch.object(cohost.subprocess, "run", return_value=result), self.assertRaises(ValueError):
                cohost.parameters(Path("."), "a" * 40, IMAGE)

    def fake_aws(self, statuses):
        responses = iter(statuses)
        def aws(args):
            if args[2] == "send-command":
                path = Path(args[args.index("--parameters") + 1].removeprefix("file://"))
                self.assertTrue(path.is_file())
                self.assertEqual(json.loads(path.read_text()), {"commands": ["fixture"]})
                return COMMAND_ID
            self.assertEqual(args[2], "get-command-invocation")
            self.assertIn("{Status:Status,ResponseCode:ResponseCode}", args)
            return next(responses)
        return aws

    def test_pending_then_success_and_terminal_failures(self):
        with contextlib.redirect_stdout(io.StringIO()):
            cohost.deliver(NODE, "a" * 40, {"commands": ["fixture"]}, self.fake_aws([
                {"Status": "Pending"}, {"Status": "InProgress"}, {"Status": "Success", "ResponseCode": 0}]), sleep=lambda _: None)
            for status in ("Failed", "Cancelled", "TimedOut", "Unknown", "Success"):
                with self.assertRaises(RuntimeError):
                    cohost.deliver(NODE, "a" * 40, {"commands": ["fixture"]}, self.fake_aws([
                        {"Status": status, "ResponseCode": 1}]), sleep=lambda _: None)

    def test_timeout_and_invalid_node_never_report_success(self):
        with self.assertRaises(ValueError):
            cohost.deliver("i-" + "0" * 17, "a" * 40, {}, aws=lambda _: self.fail("Must not call AWS"))
        clock = iter([0, cohost.POLL_SECONDS + 1])
        with self.assertRaisesRegex(RuntimeError, "unconfirmed"), contextlib.redirect_stdout(io.StringIO()):
            cohost.deliver(NODE, "a" * 40, {"commands": ["fixture"]}, self.fake_aws([]), clock=lambda: next(clock))

    def test_only_invocation_propagation_delay_is_retryable(self):
        args = ["aws", "ssm", "get-command-invocation"]
        for stderr in ("(AccessDeniedException) fixture-secret", "network failure fixture-secret"):
            with patch.object(cohost.subprocess, "run", return_value=SimpleNamespace(returncode=1, stderr=stderr)):
                with self.assertRaisesRegex(RuntimeError, "sensitive output suppressed") as error:
                    cohost.command(args)
                self.assertNotIn("fixture-secret", str(error.exception))
        with patch.object(cohost.subprocess, "run", return_value=SimpleNamespace(returncode=1, stderr="(InvocationDoesNotExist)")):
            self.assertEqual(cohost.command(args), {"Status": "Pending"})


if __name__ == "__main__":
    unittest.main()
