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
import zipfile
from unittest.mock import patch

import install_release as installer


def payload():
    # Deliberately synthetic registry/account/digest, never an operational value.
    return {"source": "a" * 40,
            "image": "000000000000.dkr.ecr.us-east-1.amazonaws.com/fixture@sha256:" + "b" * 64,
            "migrations": {"1": {"script": "V1__Initial_schema.sql", "description": "Initial schema", "checksum": 1}},
            "files": {name: {"content": base64.b64encode(data).decode(), "sha256": hashlib.sha256(data).hexdigest()}
                      for name, data in {"deploy.py": b"# candidate deploy\n", "init-db.sh": b"# candidate init\n"}.items()}}


class Installation(unittest.TestCase):
    def setUp(self):
        gate = patch.object(installer, "preflight")
        self.preflight = gate.start()
        self.addCleanup(gate.stop)
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        for name in ("opt/abservice/host_deploy.py", "etc/abservice/cohost.json", "etc/abservice/release.json",
                     "var/lib/abservice/cohost/initialization.json", "var/lib/abservice/cohost/current.json",
                     "opt/abservice/infra/host/cohost/deploy.py", "opt/abservice/infra/host/cohost/init-db.sh"):
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("{}")

    def test_incompatible_candidate_leaves_every_host_file_unchanged(self):
        before = {p: p.read_bytes() for p in self.root.rglob("*") if p.is_file()}
        self.preflight.side_effect = ValueError("incompatible history")
        with patch.object(installer.subprocess, "run") as child, self.assertRaises(ValueError):
            installer.install(payload(), self.root, child)
        child.assert_not_called()
        self.preflight.assert_called_once()
        for path, content in before.items():
            self.assertEqual(path.read_bytes(), content)

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
            lambda p: p.pop("migrations"),
            lambda p: p.update(migrations={}),
            lambda p: p["migrations"]["1"].update(checksum=True),
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


class MigrationGate(unittest.TestCase):
    def manifest(self, files):
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, "w") as archive:
            for name, content in files:
                archive.writestr(name, content)
        stream.seek(0)
        return installer.migration_manifest(stream)

    def test_checksum_ignores_bom_and_cr_lf_but_not_sql_changes(self):
        name = "db/migration/V1__Initial_schema.sql"
        base = self.manifest([(name, "-- comment\nselect 1;\n")])
        self.assertEqual(base, self.manifest([(name, "\ufeff-- comment\r\nselect 1;\r")]))
        self.assertNotEqual(base, self.manifest([(name, "-- comment\nselect 2;\n")]))

    def test_forward_and_same_history_pass_but_missing_changed_or_failed_history_fails(self):
        migrations = self.manifest([(f"db/migration/V{v}__Migration.sql", f"select {v};") for v in (1, 2)])
        history = [dict(migrations["1"], version="1", type="SQL", success=True)]
        installer.check_history(migrations, history)
        installer.check_history(migrations, [dict(history[0], script="db/migration/" + history[0]["script"])])
        installer.check_history({"1": migrations["1"]}, history)
        for change in ({"version": "3"}, {"checksum": None}, {"checksum": 0}, {"success": False},
                       {"type": "BASELINE"}, {"script": "other.sql"}, {"description": "Changed"}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                installer.check_history(migrations, [dict(history[0], **change)])
        for invalid in ([], None, history + history, [dict(migrations["2"], version="2", type="SQL", success=True)]):
            with self.assertRaises(ValueError):
                installer.check_history(migrations, invalid)

    def test_unknown_layout_or_duplicate_versions_fail_closed(self):
        for files in ([], [("db/migration/R__Repeat.sql", "select 1;")],
                      [("db/migration/V1_1__Fraction.sql", "select 1;")],
                      [("db/migration/V1__One.sql", "select 1;"), ("db/migration/V1__Two.sql", "select 2;")]):
            with self.assertRaises(ValueError):
                self.manifest(files)

    def test_command_failures_do_not_start_candidate_and_cleanup_copy_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            state = Path(directory)
            (state / "current.json").write_text(json.dumps({"name": "fixture", "compose": {
                "services": {"postgres": {"environment": {"POSTGRES_DB": "fixture"}}}}}))
            for failure in ("create", "cp"):
                calls = []
                def command(args, **kwargs):
                    calls.append(args)
                    return SimpleNamespace(returncode=int(args[1] == failure), stdout=b"fixture-secret")
                with self.assertRaisesRegex(RuntimeError, "preflight command failed"):
                    installer.image_migrations(payload()["image"], command)
                self.assertFalse(any("start" in args or "up" in args for args in calls))
                self.assertEqual(calls[-1][:3], ["docker", "rm", "-fv"])

    def test_database_command_is_read_only_and_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            state = Path(directory)
            (state / "current.json").write_text(json.dumps({"name": "fixture", "compose": {
                "services": {"postgres": {"environment": {"POSTGRES_DB": "fixture"}}}}}))
            migrations = payload()["migrations"]
            history = [dict(migrations["1"], version="1", type="SQL", success=True)]
            def command(args, **kwargs):
                self.assertIn("BEGIN READ ONLY", args[-1])
                self.assertIn("public.flyway_schema_history", args[-1])
                self.assertIn("statement_timeout", args[-1])
                self.assertIn("-X", args)
                return SimpleNamespace(returncode=0, stdout=json.dumps(history).encode())
            installer.preflight(migrations, state, command)
            with self.assertRaises(RuntimeError):
                installer.preflight(migrations, state, lambda *a, **k: SimpleNamespace(returncode=1, stdout=b"secret"))


if __name__ == "__main__":
    unittest.main()
