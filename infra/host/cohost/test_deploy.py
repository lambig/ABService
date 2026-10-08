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
from types import SimpleNamespace

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

    def test_private_audio_is_opt_in_and_keeps_database_unchanged(self):
        disabled = deploy.compose_config(self.config, IMAGE, VALUES)
        self.assertEqual(disabled["services"]["backend"]["environment"]["ABSERVICE_PRIVATE_AUDIO_ENABLED"], "false")
        self.assertEqual(len(disabled["services"]["backend"]["volumes"]), 1)
        self.config["private_audio"] = {"enabled": True, "temporary_dir": "/audio-fixture"}
        values = {**VALUES, "private-audio/bucket": "invalid-private-audio-fixture"}
        enabled = deploy.compose_config(self.config, IMAGE, values)
        self.assertEqual(enabled["services"]["postgres"], disabled["services"]["postgres"])
        self.assertEqual(enabled["volumes"], disabled["volumes"])
        backend = enabled["services"]["backend"]
        self.assertEqual(backend["environment"]["ABSERVICE_PRIVATE_AUDIO_ENABLED"], "true")
        self.assertEqual(backend["environment"]["ABSERVICE_PRIVATE_AUDIO_BUCKET"], values["private-audio/bucket"])
        self.assertNotIn("ABSERVICE_PRIVATE_AUDIO_INPUT_TIMEOUT", backend["environment"])
        extended = deploy.compose_config(self.config, IMAGE, values, input_timeout_seconds=600)
        self.assertEqual(extended["services"]["backend"]["environment"]["ABSERVICE_PRIVATE_AUDIO_INPUT_TIMEOUT"], "PT600S")
        self.assertEqual(extended["services"]["postgres"], disabled["services"]["postgres"])
        self.config["private_audio"]["enabled"] = False
        stopped = deploy.compose_config(self.config, IMAGE, VALUES, input_timeout_seconds=600)
        self.assertNotIn("ABSERVICE_PRIVATE_AUDIO_INPUT_TIMEOUT", stopped["services"]["backend"]["environment"])
        self.config["private_audio"]["enabled"] = True
        self.assertEqual(backend["volumes"][-1], {
            "type": "bind", "source": "/audio-fixture", "target": "/var/lib/abservice/private-audio",
            "read_only": False, "bind": {"create_host_path": False}})
        requested = []
        def aws(*args):
            key = args[args.index("--name") + 1].removeprefix("/fixture/test/")
            requested.append(key)
            return json.dumps({"Parameter": {"Value": values[key]}})
        with patch.object(deploy, "run", side_effect=aws):
            self.assertEqual(deploy.parameters(self.config), values)
            values["private-audio/bucket"] = VALUES["assets/bucket"]
            with self.assertRaises(deploy.DeployError):
                deploy.parameters(self.config)
            values["private-audio/bucket"] = "wildcard-*"
            with self.assertRaises(deploy.DeployError):
                deploy.parameters(self.config)
            self.config["private_audio"]["enabled"] = False
            requested.clear()
            self.assertEqual(deploy.parameters(self.config), VALUES)
            self.assertNotIn("private-audio/bucket", requested)

    def test_private_audio_rejects_unsafe_paths_permissions_and_flags(self):
        auth = self.root / "auth"
        auth.mkdir()
        (auth / "config").write_text("[default]\n")
        self.config["auth_dir"] = str(auth)
        audio = self.root / "audio"
        audio.mkdir(mode=0o700)
        self.config["private_audio"] = {"enabled": True, "temporary_dir": str(audio)}
        original_stat = Path.stat
        def fake_stat(path, *args, **kwargs):
            if path == audio:
                return SimpleNamespace(st_mode=0o40700, st_uid=1000, st_gid=1000)
            return original_stat(path, *args, **kwargs)
        with patch.object(Path, "stat", fake_stat):
            deploy.validate(self.config, IMAGE, SOURCE)
            self.config["private_audio"]["input_timeout_seconds"] = 120
            with self.assertRaises(deploy.DeployError):
                deploy.validate(self.config, IMAGE, SOURCE)
            del self.config["private_audio"]["input_timeout_seconds"]
            for bad in ("true", 1, None):
                self.config["private_audio"]["enabled"] = bad
                with self.assertRaises(deploy.DeployError):
                    deploy.validate(self.config, IMAGE, SOURCE)
            self.config["private_audio"]["enabled"] = True
            for directory in (auth, self.root, Path("/"), Path("relative"), self.root / "missing"):
                self.config["private_audio"]["temporary_dir"] = str(directory)
                with self.assertRaises(deploy.DeployError):
                    deploy.validate(self.config, IMAGE, SOURCE)
            self.config["private_audio"]["temporary_dir"] = str(audio)
            with self.assertRaises(deploy.DeployError):
                deploy.deploy(self.config, IMAGE, SOURCE, audio / "state")
        for uid, gid, mode in ((0, 1000, 0o40700), (1000, 0, 0o40700), (1000, 1000, 0o40755)):
            def invalid_stat(path, *args, **kwargs):
                return SimpleNamespace(st_mode=mode, st_uid=uid, st_gid=gid) if path == audio else original_stat(path, *args, **kwargs)
            with patch.object(Path, "stat", invalid_stat), self.assertRaises(deploy.DeployError):
                deploy.validate(self.config, IMAGE, SOURCE)

    def test_runtime_file_is_optional_and_invalid_values_fail_before_deploy(self):
        import sys
        config_path = self.root / "cohost.json"
        config_path.write_text(json.dumps(self.config))
        runtime = self.root / "private-audio-runtime.json"
        argv = ["deploy.py", "--config", str(config_path), "--image", IMAGE,
                "--source", SOURCE, "--state-dir", str(self.root / "state")]
        with patch.object(sys, "argv", argv), patch.object(deploy, "deploy") as apply:
            self.assertEqual(deploy.main(), 0)
            self.assertIsNone(apply.call_args.args[-1])
            for seconds in (1, 120, 600):
                runtime.write_text(json.dumps({"input_timeout_seconds": seconds}))
                self.assertEqual(deploy.main(), 0)
                self.assertEqual(apply.call_args.args[-1], seconds)
            invalid = [{"input_timeout_seconds": s} for s in (0, -1, 601, True, False, 120.5, "120", None)]
            invalid += [{}, [], None, {"input_timeout_seconds": 120, "unknown": 1}]
            for value in invalid:
                runtime.write_text(json.dumps(value))
                apply.reset_mock()
                with contextlib.redirect_stderr(io.StringIO()):
                    self.assertEqual(deploy.main(), 1)
                apply.assert_not_called()
            runtime.write_text("{invalid-secret")
            with contextlib.redirect_stderr(io.StringIO()) as error:
                self.assertEqual(deploy.main(), 1)
            self.assertNotIn("invalid-secret", error.getvalue())
            apply.assert_not_called()

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
