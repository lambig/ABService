"""Opt-in system manager smoke test. Run with sudo; never contacts AWS.

Creates uniquely named units under /run, runs as nobody, then removes only those units
and directories. Requires a disposable Linux validation host with systemd as PID 1.
"""

import json
import os
from pathlib import Path
import pwd
import shutil
import subprocess
import tempfile
import time


SOURCE = Path(__file__).resolve().parent


def systemctl(*args):
    return subprocess.run(["systemctl", *args], check=True, capture_output=True, text=True).stdout


def await_condition(check, description):
    deadline = time.monotonic() + 25
    while time.monotonic() < deadline:
        if check():
            return
        time.sleep(0.2)
    raise AssertionError(description)


def main():
    if os.geteuid() != 0:
        raise SystemExit("Run explicitly with sudo on a disposable systemd validation host")
    account = pwd.getpwnam("nobody")
    directory = Path(tempfile.mkdtemp(prefix="ab-monitor-test-", dir="/run"))
    name = directory.name
    runtime = Path("/run") / (name + "-credentials")
    service = Path("/run/systemd/system") / (name + ".service")
    timer = service.with_suffix(".timer")
    try:
        directory.chmod(0o755)
        shutil.copyfile(SOURCE / "refresh.py", directory / "refresh.py")
        (directory / "refresh.py").chmod(0o644)
        generation = directory / "generation"
        generation.mkdir(mode=0o700)
        os.chown(generation, account.pw_uid, account.pw_gid)
        for filename in ("certificate.pem", "private-key.pem"):
            path = generation / filename
            path.write_text("deliberately invalid test material")
            os.chown(path, account.pw_uid, account.pw_gid)
            path.chmod(0o600)
        helper = directory / "helper"
        helper.write_text(
            "#!/usr/bin/python3\nimport datetime, json, pathlib, sys\n"
            "if pathlib.Path(__file__).with_name('fail').exists():\n"
            "    sys.exit(1)\n"
            "now = datetime.datetime.now(datetime.timezone.utc)\n"
            "print(json.dumps({'Version': 1, 'AccessKeyId': 'INVALID_SYSTEMD_TEST',\n"
            " 'SecretAccessKey': 'INVALID_TEST_SECRET', 'SessionToken': 'INVALID_TEST_TOKEN',\n"
            " 'Expiration': (now + datetime.timedelta(seconds=900)).isoformat()}))\n")
        helper.chmod(0o755)
        config = directory / "config.json"
        config.write_text(json.dumps({"helper": str(helper), "certificate_directory": str(generation),
                                     "trust_anchor_arn": "invalid-test-anchor", "profile_arn": "invalid-test-profile",
                                     "role_arn": "invalid-test-role"}))
        config.chmod(0o644)
        unit = (SOURCE / "abservice-monitor-credentials.service").read_text()
        # Change only installation locations and the test identity; retain sandboxing/lifecycle.
        for old, new in {
            "User=abservice-monitor": "User=nobody",
            "Group=abservice-monitor": f"Group={account.pw_gid}",
            "RuntimeDirectory=abservice-monitor": f"RuntimeDirectory={runtime.name}",
            "/opt/abservice/monitor-credentials/refresh.py": str(directory / "refresh.py"),
            "/etc/abservice/monitor-credentials.json": str(config),
            "/run/abservice-monitor": str(runtime),
        }.items():
            assert unit.count(old) == 1, old
            unit = unit.replace(old, new)
        service.write_text(unit)
        service.chmod(0o644)
        schedule = (SOURCE / "abservice-monitor-credentials.timer").read_text()
        for old, new in {"OnBootSec=15s": "OnActiveSec=1s", "OnUnitActiveSec=120s": "OnUnitActiveSec=3s",
                         "abservice-monitor-credentials.service": service.name}.items():
            assert schedule.count(old) == 1, old
            schedule = schedule.replace(old, new)
        timer.write_text(schedule)
        timer.chmod(0o644)
        subprocess.run(["systemd-analyze", "verify", str(service), str(timer)], check=True)
        systemctl("daemon-reload")
        systemctl("start", timer.name)

        def status():
            try:
                return json.loads((runtime / "status.json").read_text())
            except FileNotFoundError:
                return {}

        await_condition(lambda: status().get("result") == "success", "initial refresh did not run")
        original = (runtime / "credentials").read_bytes()
        first_success = status()["last_success_at"]
        (directory / "fail").touch()
        await_condition(lambda: status().get("result") == "failure", "timer did not retry with failed helper")
        assert (runtime / "credentials").read_bytes() == original
        assert status()["last_success_at"] == first_success
        assert systemctl("show", service.name, "--property=Result", "--value").strip() == "exit-code"
        (directory / "fail").unlink()
        await_condition(lambda: status().get("result") == "success"
                        and status().get("last_success_at") != first_success, "timer did not recover after failure")
        systemctl("stop", timer.name, service.name)
        assert (runtime / "credentials").is_file(), "oneshot stop removed credentials"
        assert runtime.stat().st_mode & 0o777 == 0o700
        assert (runtime / "credentials").stat().st_mode & 0o777 == 0o600
        assert (runtime / "credentials").stat().st_uid == account.pw_uid
        # Simulate /run being empty after a reboot; activation must recreate it.
        shutil.rmtree(runtime)
        systemctl("start", timer.name)
        await_condition(lambda: status().get("result") == "success", "empty-runtime restart failed")
        print("systemd smoke: initial refresh, failure retention, timer recovery, stop preservation, empty-runtime restart passed")
    finally:
        subprocess.run(["systemctl", "stop", timer.name, service.name], capture_output=True)
        subprocess.run(["systemctl", "reset-failed", service.name], capture_output=True)
        service.unlink(missing_ok=True)
        timer.unlink(missing_ok=True)
        systemctl("daemon-reload")
        for path in (runtime, directory):
            assert path.parent == Path("/run") and path.name.startswith("ab-monitor-test-")
            if path.exists():
                shutil.rmtree(path)
        assert not any(path.exists() for path in (service, timer, runtime, directory))
        print("systemd smoke: temporary units and directories removed")


if __name__ == "__main__":
    main()
