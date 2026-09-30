import base64
import contextlib
import fcntl
import hashlib
import io
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import install_release as installer


def payload():
    # Deliberately synthetic registry/account/digest, never an operational value.
    return {"source": "a" * 40,
            "image": "000000000000.dkr.ecr.us-east-1.amazonaws.com/fixture@sha256:" + "b" * 64,
            "files": {name: {"content": base64.b64encode(data).decode(), "sha256": hashlib.sha256(data).hexdigest()}
                      for name, data in {"deploy.py": b"# candidate deploy\n", "init-db.sh": b"# candidate init\n"}.items()}}


class Installation(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        for name in ("opt/abservice/host_deploy.py", "etc/abservice/cohost.json", "etc/abservice/release.json",
                     "var/lib/abservice/cohost/initialization.json", "var/lib/abservice/cohost/current.json",
                     "opt/abservice/infra/host/cohost/deploy.py", "opt/abservice/infra/host/cohost/init-db.sh"):
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("{}")

    def healthy(self, args, **kwargs):
        self.assertEqual(args, ["python3", str(self.root / "opt/abservice/host_deploy.py"), "update"])
        release = json.loads((self.root / "etc/abservice/release.json").read_text())
        scripts = self.root / "opt/abservice/infra/host/cohost"
        self.assertEqual((scripts / "deploy.py").read_bytes(), b"# candidate deploy\n")
        self.assertEqual((scripts / "init-db.sh").read_bytes(), b"# candidate init\n")
        state = self.root / "var/lib/abservice/cohost"
        (state / "current.json").write_text(json.dumps(release))
        (state / "attempt.json").write_text(json.dumps(dict(release, status="healthy")))
        return SimpleNamespace(returncode=0, stdout=b"fixture-secret", stderr=b"fixture-secret")

    def test_update_retry_and_rollback_preserve_operator_configuration(self):
        original = {name: (self.root / name).read_bytes() for name in (
            "opt/abservice/host_deploy.py", "etc/abservice/cohost.json", "var/lib/abservice/cohost/initialization.json")}
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            installer.install(payload(), self.root, self.healthy)
            installer.install(payload(), self.root, self.healthy)
            older = payload()
            older["source"] = "c" * 40
            installer.install(older, self.root, self.healthy)
        self.assertNotIn("fixture-secret", output.getvalue())
        for name, data in original.items():
            self.assertEqual((self.root / name).read_bytes(), data)
        self.assertEqual(json.loads((self.root / "etc/abservice/release.json").read_text())["source"], "c" * 40)

    def test_invalid_or_incomplete_payload_does_not_change_host(self):
        for mutate in (
            lambda p: p.update(source="$(touch /tmp/unsafe)"),
            lambda p: p.update(image="repo:latest"),
            lambda p: p["files"].pop("init-db.sh"),
            lambda p: p["files"].update({"../escape": p["files"]["deploy.py"]}),
            lambda p: p["files"]["deploy.py"].update(sha256="0" * 64),
        ):
            data = payload()
            mutate(data)
            with self.assertRaises(ValueError):
                installer.install(data, self.root, self.healthy)
            self.assertEqual((self.root / "etc/abservice/release.json").read_text(), "{}")

    def test_refuses_uninitialized_host_before_changes(self):
        (self.root / "var/lib/abservice/cohost/initialization.json").unlink()
        with self.assertRaisesRegex(ValueError, "provisioned"):
            installer.install(payload(), self.root, self.healthy)
        self.assertEqual((self.root / "etc/abservice/release.json").read_text(), "{}")

    def test_lock_prevents_script_replacement_during_another_delivery(self):
        with (self.root / "var/lib/abservice/cohost-delivery.lock").open("a") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            with self.assertRaises(BlockingIOError):
                installer.install(payload(), self.root, self.healthy)
        self.assertEqual((self.root / "etc/abservice/release.json").read_text(), "{}")

    def test_failed_install_does_not_call_entrypoint_and_retry_repairs_pair(self):
        with patch.object(installer, "replace", side_effect=OSError("write failed")), patch.object(installer, "subprocess") as child:
            with self.assertRaises(OSError):
                installer.install(payload(), self.root, child.run)
            child.run.assert_not_called()
        installer.install(payload(), self.root, self.healthy)

    def test_failed_or_unverified_update_is_not_success(self):
        for action in (lambda *a, **kw: SimpleNamespace(returncode=1),
                       lambda *a, **kw: SimpleNamespace(returncode=0)):
            with self.assertRaises((RuntimeError, FileNotFoundError)):
                installer.install(payload(), self.root, action)

    def test_stale_healthy_attempt_is_rejected(self):
        def stale(*args, **kwargs):
            result = self.healthy(*args, **kwargs)
            path = self.root / "var/lib/abservice/cohost/attempt.json"
            attempt = json.loads(path.read_text())
            attempt["source"] = "c" * 40
            path.write_text(json.dumps(attempt))
            return result
        with self.assertRaisesRegex(RuntimeError, "confirm"):
            installer.install(payload(), self.root, stale)


if __name__ == "__main__":
    unittest.main()
