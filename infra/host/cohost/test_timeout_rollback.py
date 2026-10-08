"""Retained pre-#538 deploy code must accept unchanged operator configuration."""
import base64
import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import sys
import tempfile
from types import ModuleType, SimpleNamespace
import unittest
from unittest.mock import patch

import install_release as installer
from test_deploy import config, VALUES
from test_install_release import payload


BASE = "bf1727026d01777abf7556ff04ef6457e126ead3"
HERE = Path(__file__).parent


class TimeoutRollback(unittest.TestCase):
    def test_standard_install_rollback_and_reapply_keep_runtime_file(self):
        # Exact public deploy.py from BASE, retained so shallow CI needs no network/history.
        old_script = (HERE / "fixtures/pre538-deploy.py.txt").read_bytes()
        self.assertEqual(hashlib.sha256(old_script).hexdigest(),
                         "589cd5b01b2c382efb70150af9ca6a74db2384beb1f5c32be30be0182de35a3b")
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            scripts = root / "opt/abservice/infra/host/cohost"
            settings = root / "etc/abservice"
            state = root / "var/lib/abservice/cohost"
            auth = root / "auth"
            audio = root / "audio"
            for path in (scripts, settings, state, auth, audio):
                path.mkdir(parents=True, mode=0o700)
            entry = root / "opt/abservice/host_deploy.py"
            entry.write_text("# fixed operator entrypoint fixture\n")
            (auth / "config").write_text("[default]\n")
            configuration = config(auth)
            configuration["private_audio"] = {"enabled": True, "temporary_dir": str(audio)}
            (settings / "cohost.json").write_text(json.dumps(configuration))
            runtime = settings / "private-audio-runtime.json"
            runtime.write_text('{"input_timeout_seconds": 600}\n')
            original = {path: path.read_bytes() for path in (entry, runtime, settings / "cohost.json")}
            values = {**VALUES, "private-audio/bucket": "invalid-private-audio-fixture"}
            models = []
            commands = []
            stat = Path.stat

            def audio_stat(path, *args, **kwargs):
                if path == audio:
                    return SimpleNamespace(st_mode=0o40700, st_uid=1000, st_gid=1000)
                return stat(path, *args, **kwargs)

            def external(*args, **kwargs):
                commands.append(args)
                if args[0] == "aws":
                    key = args[args.index("--name") + 1].removeprefix("/fixture/test/")
                    return json.dumps({"Parameter": {"Value": values[key]}})
                if args[:3] == ("docker", "volume", "inspect"):
                    identity = json.loads((state / "initialization.json").read_text())["id"]
                    return json.dumps([{"Labels": {"abservice.cohost": configuration["name"],
                                                   "abservice.cohost.state": identity}}])
                if args[:2] == ("docker", "compose") and "up" in args:
                    model = json.loads(Path(args[args.index("-f") + 1]).read_text())
                    models.append(model)
                if args[-1] == "{{.Architecture}}":
                    return "amd64"
                if args[:2] == ("docker", "inspect"):
                    return models[-1]["services"]["backend"]["image"]
                if args[:3] == ("docker", "image", "inspect") and args[-1] == "{{.Id}}":
                    return args[3]
                return ""

            def invoke(initialize=False):
                module = ModuleType("installed_deploy")
                module.__file__ = str(scripts / "deploy.py")
                exec(compile((scripts / "deploy.py").read_bytes(), module.__file__, "exec"), module.__dict__)
                release = json.loads((settings / "release.json").read_text())
                argv = [module.__file__, "--config", str(settings / "cohost.json"),
                        "--state-dir", str(state), "--source", release["source"], "--image", release["image"]]
                if initialize:
                    argv.append("--initialize")
                with patch.object(sys, "argv", argv), patch.object(module, "run", side_effect=external):
                    self.assertEqual(module.main(), 0)

            def fixed_entrypoint(args, **kwargs):
                self.assertEqual(args, ["python3", str(entry), "update"])
                invoke()
                return SimpleNamespace(returncode=0)

            def release(script, source, digest):
                data = payload()
                data["source"] = source
                data["image"] = data["image"].split("@sha256:")[0] + "@sha256:" + digest * 64
                data["files"] = {
                    name: {"content": base64.b64encode(content).decode(),
                           "sha256": hashlib.sha256(content).hexdigest()}
                    for name, content in {"deploy.py": script, "init-db.sh": (HERE / "init-db.sh").read_bytes()}.items()}
                return data

            older = release(old_script, BASE, "c")
            newer = release((HERE / "deploy.py").read_bytes(), "a" * 40, "b")
            (scripts / "deploy.py").write_bytes(old_script)
            (scripts / "init-db.sh").write_bytes((HERE / "init-db.sh").read_bytes())
            (settings / "release.json").write_text(json.dumps({k: older[k] for k in ("source", "image")}))
            previous_umask = os.umask(0o077)
            try:
                with patch.object(Path, "stat", audio_stat), contextlib.redirect_stdout(io.StringIO()):
                    invoke(initialize=True)
                    identity = (state / "initialization.json").read_bytes()
                    # Migration preflight has its own real-history tests; only external IO is simulated here.
                    with patch.object(installer, "preflight"):
                        for data, expected in ((newer, "PT600S"), (older, None), (newer, "PT600S")):
                            installer.install(data, root, fixed_entrypoint)
                            current = json.loads((state / "current.json").read_text())
                            self.assertEqual(current["source"], data["source"])
                            self.assertEqual(current["image"], data["image"])
                            backend = current["compose"]["services"]["backend"]
                            self.assertEqual(backend["environment"].get("ABSERVICE_PRIVATE_AUDIO_INPUT_TIMEOUT"), expected)
                            self.assertEqual(backend["environment"]["ABSERVICE_PRIVATE_AUDIO_ENABLED"], "true")
                            self.assertEqual((scripts / "deploy.py").read_bytes(),
                                             base64.b64decode(data["files"]["deploy.py"]["content"]))
                            self.assertEqual((state / "initialization.json").read_bytes(), identity)
                            for path, content in original.items():
                                self.assertEqual(path.read_bytes(), content)
            finally:
                os.umask(previous_umask)
            self.assertEqual(len(models), 4)
            for model in models[1:]:
                self.assertEqual(model["services"]["postgres"], models[0]["services"]["postgres"])
                self.assertEqual(model["volumes"], models[0]["volumes"])
            self.assertEqual(sum(args[:3] == ("docker", "volume", "create") for args in commands), 1)


if __name__ == "__main__":
    unittest.main()
