import contextlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import deploy


VALUES = {
    "db/name": "fixture", "db/username": "fixture_app", "db/password": "invalid-$'\"\\\n-db",
    "db/admin-password": "invalid-admin-only", "app/admin-api-key": "invalid-api-only",
    "app/origin-verify-token": "invalid-origin-only", "assets/bucket": "invalid-fixture-bucket",
}
IMAGE = "example.invalid/backend@sha256:" + "a" * 64
SOURCE = "b" * 40


def config(auth_dir):
    return {"name": "fixture-cohost", "region": "us-east-1", "parameter_prefix": "/fixture/test",
            "postgres_image": "postgres@sha256:" + "c" * 64,
            "auth_dir": str(auth_dir), "bind_address": "127.0.0.1", "port": 18080}


class DeployTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / "config").write_text("[default]\n")
        self.config = config(self.root)

    def test_unpinned_images_and_missing_auth_fail_before_deploy(self):
        for image in ("backend:latest", "sha256:" + "a" * 64, IMAGE + "\n"):
            with self.assertRaises(deploy.DeployError):
                deploy.validate(self.config, image, SOURCE)
        (self.root / "config").unlink()
        with self.assertRaises(deploy.DeployError):
            deploy.validate(self.config, IMAGE, SOURCE)

    def test_parameter_values_are_not_evaluated_and_failures_do_not_leak(self):
        def aws(*args, **kwargs):
            key = args[args.index("--name") + 1].removeprefix("/fixture/test/")
            self.assertIn("--with-decryption", args)
            return json.dumps({"Parameter": {"Value": VALUES[key]}})
        with patch.object(deploy, "run", side_effect=aws):
            self.assertEqual(deploy.parameters(self.config), VALUES)
        bad = subprocess.CompletedProcess(["aws"], 1, "secret-output", "secret-error")
        with patch.object(subprocess, "run", return_value=bad):
            with self.assertRaises(deploy.DeployError) as caught:
                deploy.run("aws", "--secret=do-not-print")
        self.assertNotIn("secret", str(caught.exception))

    def test_compose_secret_interpolation_and_db_isolation(self):
        model = deploy.compose_config(self.config, IMAGE, VALUES)
        db = model["services"]["postgres"]
        self.assertNotIn("ports", db)
        self.assertEqual(db["networks"], ["database"])
        self.assertTrue(model["networks"]["database"]["internal"])
        self.assertTrue(model["volumes"]["data"]["external"])
        self.assertEqual(model["services"]["backend"]["environment"]["DB_PASSWORD"], VALUES["db/password"].replace("$", "$$"))
        self.assertNotIn("AWS_ACCESS_KEY_ID", model["services"]["backend"]["environment"])

    def test_failed_health_keeps_current_and_records_failed_attempt(self):
        state = self.root / "state"
        state.mkdir(mode=0o700)
        calls = []

        def fake_run(*args, **kwargs):
            calls.append(args)
            if args[:3] == ("docker", "volume", "inspect"):
                identity = deploy.read_json(state / "initialization.json")["id"]
                return json.dumps([{"Labels": {"abservice.cohost": self.config["name"],
                                               "abservice.cohost.state": identity}}])
            if args[-1] == "{{.Architecture}}":
                return "amd64"
            return ""

        with patch.object(deploy, "parameters", return_value=VALUES), patch.object(deploy, "run", side_effect=fake_run):
            with patch.object(deploy, "compose", return_value="container"), contextlib.redirect_stdout(io.StringIO()):
                deploy.deploy(self.config, IMAGE, SOURCE, state, initialize=True)
            before = (state / "current.json").read_bytes()

            def unhealthy(*args):
                if "up" in args:
                    raise deploy.DeployError("unhealthy fixture")
                return ""
            with patch.object(deploy, "compose", side_effect=unhealthy), self.assertRaises(deploy.DeployError):
                deploy.deploy(self.config, IMAGE.replace("a" * 64, "d" * 64), SOURCE, state)
            self.assertEqual((state / "current.json").read_bytes(), before)
            self.assertEqual(deploy.read_json(state / "attempt.json")["status"], "failed")
            self.assertEqual((state / "current.json").stat().st_mode & 0o777, 0o600)
            self.assertFalse(any("prune" in call or "rm" in call for call in calls))
            with patch.object(deploy, "parameters", return_value={**VALUES, "db/password": "changed"}):
                with self.assertRaisesRegex(deploy.DeployError, "Database configuration changed"):
                    deploy.deploy(self.config, IMAGE, SOURCE, state)
            self.assertEqual((state / "current.json").read_bytes(), before)
            self.assertEqual(deploy.read_json(state / "attempt.json")["status"], "failed")

    def test_existing_volume_is_never_initialized(self):
        with patch.object(deploy, "parameters", return_value=VALUES), patch.object(deploy, "run", return_value="fixture-cohost-postgres\n") as runner:
            with self.assertRaisesRegex(deploy.DeployError, "existing volume"):
                deploy.deploy(self.config, IMAGE, SOURCE, self.root / "state", initialize=True)
        self.assertEqual(runner.call_count, 1)

    def test_state_without_initialization_cannot_adopt_volume(self):
        with patch.object(deploy, "run") as runner, patch.object(deploy, "parameters") as params:
            with self.assertRaisesRegex(deploy.DeployError, "No initialization record"):
                deploy.deploy(self.config, IMAGE, SOURCE, self.root / "mistyped-state")
        runner.assert_not_called()
        params.assert_not_called()

    def test_failed_initialization_retries_only_same_database_and_state(self):
        state = self.root / "state"
        labels = {}

        def fake_run(*args, **kwargs):
            if args[:3] == ("docker", "volume", "create"):
                labels.update({"abservice.cohost": self.config["name"],
                               "abservice.cohost.state": deploy.read_json(state / "initialization.json")["id"]})
            if args[:3] == ("docker", "volume", "inspect"):
                return json.dumps([{"Labels": labels}])
            if args[-1] == "{{.Architecture}}":
                return "amd64"
            return ""

        def unhealthy(*args):
            if "up" in args:
                raise deploy.DeployError("unhealthy fixture")
            return ""

        with patch.object(deploy, "parameters", return_value=VALUES), patch.object(deploy, "run", side_effect=fake_run) as runner:
            with patch.object(deploy, "compose", side_effect=unhealthy), self.assertRaises(deploy.DeployError):
                deploy.deploy(self.config, IMAGE, SOURCE, state, initialize=True)
            self.assertFalse((state / "current.json").exists())
            identity = (state / "initialization.json").read_bytes()
            self.assertEqual((state / "initialization.json").stat().st_mode & 0o777, 0o600)

            for replacement in ({**VALUES, "db/password": "changed"}, VALUES):
                changed_config = self.config if replacement != VALUES else {**self.config, "postgres_image": IMAGE}
                runner.reset_mock()
                with patch.object(deploy, "parameters", return_value=replacement):
                    with self.assertRaisesRegex(deploy.DeployError, "Database configuration changed"):
                        deploy.deploy(changed_config, IMAGE, SOURCE, state)
                runner.assert_not_called()

            copied = self.root / "copied-state"
            shutil.copytree(state, copied)
            runner.reset_mock()
            with self.assertRaisesRegex(deploy.DeployError, "another state directory"):
                deploy.deploy(self.config, IMAGE, SOURCE, copied)
            runner.assert_not_called()

            # A project label alone is insufficient, including after a creation race.
            labels["abservice.cohost.state"] = "another-state-id"
            with patch.object(deploy, "compose") as compose:
                with self.assertRaisesRegex(deploy.DeployError, "ownership/state labels"):
                    deploy.deploy(self.config, IMAGE, SOURCE, state)
                compose.assert_not_called()
            labels["abservice.cohost.state"] = json.loads(identity)["id"]
            with patch.object(deploy, "compose", return_value="container"), contextlib.redirect_stdout(io.StringIO()):
                deploy.deploy(self.config, IMAGE, SOURCE, state)
            self.assertTrue((state / "current.json").exists())
            self.assertEqual((state / "initialization.json").read_bytes(), identity)


if __name__ == "__main__":
    unittest.main()
