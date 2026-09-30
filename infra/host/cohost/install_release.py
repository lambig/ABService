"""Install a verified update for the operator-provisioned fixed deployment entrypoint.

This does not provision credentials/configuration or initialize a database.
"""
import base64
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

FILES = ("deploy.py", "init-db.sh")


def validate(payload):
    if set(payload) != {"source", "image", "files"}:
        raise ValueError("Invalid release fields")
    if not re.fullmatch(r"[a-f0-9]{40}", payload["source"]):
        raise ValueError("Invalid source")
    if not re.fullmatch(r"[0-9]{12}\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com/[a-z0-9][a-z0-9._/-]*@sha256:[a-f0-9]{64}", payload["image"]):
        raise ValueError("Invalid image")
    if set(payload["files"]) != set(FILES):
        raise ValueError("Invalid file set")
    decoded = {}
    for name in FILES:
        entry = payload["files"][name]
        if set(entry) != {"content", "sha256"}:
            raise ValueError("Invalid file record")
        data = base64.b64decode(entry["content"], validate=True)
        if not data or hashlib.sha256(data).hexdigest() != entry["sha256"]:
            raise ValueError("Invalid file checksum")
        decoded[name] = data
    compile(decoded["deploy.py"], "deploy.py", "exec")
    return decoded


def replace(path, data):
    # Stage on the same filesystem; readers never see a partially written file.
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as stream:
        temporary = Path(stream.name)
        try:
            os.chmod(temporary, 0o600)
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        except BaseException:
            temporary.unlink(missing_ok=True)
            raise
    try:
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def install(payload, root=Path("/"), execute=subprocess.run):
    decoded = validate(payload)
    entry = root / "opt/abservice/host_deploy.py"
    scripts = root / "opt/abservice/infra/host/cohost"
    configuration = root / "etc/abservice"
    state = root / "var/lib/abservice/cohost"
    # Refuse bootstrap or a misplaced target. Operator configuration and the fixed
    # entrypoint stay outside the delivery payload, including on rollback.
    for path in (entry, configuration / "cohost.json", configuration / "release.json",
                 state / "initialization.json", state / "current.json"):
        if not path.is_file() or path.is_symlink():
            raise ValueError("Host has not been provisioned for updates")
    if not scripts.is_dir() or scripts.is_symlink():
        raise ValueError("Missing fixed script directory")
    lock = root / "var/lib/abservice/cohost-delivery.lock"
    with lock.open("a") as stream:
        os.chmod(lock, 0o600)
        fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        # Validate everything before changing either file. A failed install never
        # invokes the entrypoint; a retry writes the complete pair again.
        for name, data in decoded.items():
            replace(scripts / name, data)
        replace(configuration / "release.json", json.dumps({
            "image": payload["image"], "source": payload["source"]
        }).encode())
        # The fixed entrypoint uses the host's deploy credentials and suppresses
        # secret-bearing AWS/Docker output. Never relay even its captured output.
        # The outer GNU timeout bounds the entire process group. Killing only
        # this direct child on a Python timeout could leave its children running.
        result = execute(["python3", str(entry), "update"], capture_output=True)
        if result.returncode:
            raise RuntimeError("Host update failed")
        current = json.loads((state / "current.json").read_text())
        attempt = json.loads((state / "attempt.json").read_text())
        if (current.get("source") != payload["source"] or current.get("image") != payload["image"]
                or attempt.get("status") != "healthy" or attempt.get("source") != payload["source"]
                or attempt.get("image") != payload["image"]):
            raise RuntimeError("Host did not confirm the requested release")
    print("Verified host update completed", flush=True)


def main():
    os.umask(0o077)
    try:
        install(json.loads(base64.b64decode(sys.argv[1], validate=True)))
    except Exception:
        print("Host update failed; inspect protected host state", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
