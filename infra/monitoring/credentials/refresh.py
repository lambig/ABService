#!/usr/bin/env python3
"""Publish one Roles Anywhere session for a file-based monitoring agent (Linux)."""

import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import fcntl
import json
import os
from pathlib import Path
import selectors
import signal
import stat
import subprocess
import sys
import tempfile
import time


SESSION_SECONDS = 900
MIN_REMAINING_SECONDS = 780  # 600s agent cache + 120s timer + 60s margin.
HELPER_TIMEOUT_SECONDS = 45
# Local resource budget for the entire helper response, NOT an AWS token-size limit.
MAX_RESPONSE_BYTES = 65536


class RefreshError(Exception):
    """Only fixed, non-secret error codes may leave this process."""


def timestamp():
    return datetime.now(timezone.utc).isoformat()


def expiry(value):
    if not isinstance(value, str):
        raise ValueError("timestamp type")
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise ValueError("timestamp timezone")
    return parsed.astimezone(timezone.utc)


def private_directory(path):
    info = path.lstat()
    if (not stat.S_ISDIR(info.st_mode) or info.st_uid != os.geteuid()
            or stat.S_IMODE(info.st_mode) != 0o700):
        raise RefreshError("unsafe_runtime_directory")
    # Parents are administrator-controlled; reject symlink aliases of the runtime path.
    if path.resolve() != path:
        raise RefreshError("unsafe_runtime_directory")


@contextmanager
def exclusive(directory):
    fd = os.open(directory / "refresh.lock", os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    try:
        info = os.fstat(fd)
        if (not stat.S_ISREG(info.st_mode) or info.st_uid != os.geteuid()
                or stat.S_IMODE(info.st_mode) != 0o600 or info.st_nlink != 1):
            raise RefreshError("unsafe_lock")
        try:
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise RefreshError("already_running") from None
        yield
    finally:
        os.close(fd)


def atomic_write(directory, name, text):
    fd, temporary = tempfile.mkstemp(prefix=".refresh-", dir=directory)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as output:
            os.fchmod(output.fileno(), 0o600)
            output.write(text)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, directory / name)
        parent = os.open(directory, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(parent)
        finally:
            os.close(parent)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def load_config(path):
    config = json.loads(path.read_text(encoding="utf-8"))
    fields = {"helper", "certificate_directory", "trust_anchor_arn", "profile_arn", "role_arn"}
    if (not isinstance(config, dict) or set(config) != fields
            or any(not isinstance(v, str) or not v or v.strip() != v for v in config.values())):
        raise RefreshError("invalid_config")
    if any(not Path(config[k]).is_absolute() for k in ("helper", "certificate_directory")):
        raise RefreshError("invalid_config")
    # Resolve the generation ONCE, so a concurrent current-symlink switch cannot mix keys.
    generation = Path(config["certificate_directory"]).resolve(strict=True)
    for name in ("certificate.pem", "private-key.pem"):
        if not stat.S_ISREG((generation / name).lstat().st_mode):
            raise RefreshError("invalid_certificate_generation")
    key = (generation / "private-key.pem").stat()
    if key.st_uid != os.geteuid() or stat.S_IMODE(key.st_mode) != 0o600:
        raise RefreshError("unsafe_private_key")
    command = [config["helper"], "credential-process",
               "--certificate", str(generation / "certificate.pem"),
               "--private-key", str(generation / "private-key.pem"),
               "--trust-anchor-arn", config["trust_anchor_arn"],
               "--profile-arn", config["profile_arn"], "--role-arn", config["role_arn"],
               "--session-duration", str(SESSION_SECONDS)]
    return command


def acquire(command):
    # Do not inherit an operator's AWS credentials, debug switches or SDK config.
    environment = {"PATH": "/usr/bin:/bin", "LANG": "C.UTF-8", "HOME": "/nonexistent",
                   "AWS_EC2_METADATA_DISABLED": "true"}
    try:
        process = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                   stderr=subprocess.DEVNULL, env=environment, bufsize=0,
                                   start_new_session=True)
    except OSError:
        raise RefreshError("helper_start_failed") from None
    deadline = time.monotonic() + HELPER_TIMEOUT_SECONDS
    output = bytearray()
    try:
        with selectors.DefaultSelector() as selector:
            selector.register(process.stdout, selectors.EVENT_READ)
            while True:
                remaining = deadline - time.monotonic()
                if remaining <= 0 or not selector.select(remaining):
                    raise RefreshError("helper_timeout")
                # Buffer at most the local budget plus one byte, even for endless output.
                chunk = os.read(process.stdout.fileno(), min(8192, MAX_RESPONSE_BYTES + 1 - len(output)))
                if not chunk:
                    break
                output.extend(chunk)
                if len(output) > MAX_RESPONSE_BYTES:
                    raise RefreshError("helper_output_too_large")
        try:
            process.wait(timeout=max(0, deadline - time.monotonic()))
        except subprocess.TimeoutExpired:
            raise RefreshError("helper_timeout") from None
        if process.returncode:
            raise RefreshError("helper_failed")
        return bytes(output)
    finally:
        # Reap the helper and any descendants retaining the pipe, including on limit/timeout.
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        process.wait()
        process.stdout.close()


def validate_response(raw):
    if len(raw) > MAX_RESPONSE_BYTES:
        raise RefreshError("helper_output_too_large")
    try:
        data = json.loads(raw)
        if not isinstance(data, dict) or type(data.get("Version")) is not int or data["Version"] != 1:
            raise ValueError("response version")
        for field in ("AccessKeyId", "SecretAccessKey", "SessionToken"):
            value = data.get(field)
            # Treat credentials as opaque strings; only protect the shared INI structure.
            if (not isinstance(value, str) or not value
                    or any(character in value for character in ("\r", "\n", "\0"))):
                raise ValueError("credential format")
        remaining = (expiry(data["Expiration"]) - datetime.now(timezone.utc)).total_seconds()
        if not MIN_REMAINING_SECONDS <= remaining <= SESSION_SECONDS + 60:
            raise RefreshError("insufficient_or_invalid_lifetime")
    except (ValueError, KeyError, TypeError, OverflowError):
        raise RefreshError("invalid_helper_response") from None
    return data


def previous_success(directory):
    # Copy only validated dates; never echo arbitrary contents from an old status file.
    try:
        old = json.loads((directory / "status.json").read_text(encoding="utf-8"))
        return {field: expiry(old[field]).isoformat()
                for field in ("last_success_at", "credential_expires_at")}
    except (OSError, ValueError, KeyError, TypeError, OverflowError):
        return {"last_success_at": None, "credential_expires_at": None}


def refresh(config_path, directory):
    os.umask(0o077)
    private_directory(directory)
    with exclusive(directory):
        # A SIGKILL before rename can leave a 0600 temp file. Only remove our reserved files
        # while holding the lock; never unlink the lock inode or the last good credentials.
        for temporary in directory.glob(".refresh-*"):
            temporary.unlink()
        status = {"version": 1, "attempted_at": timestamp(), **previous_success(directory)}
        stage = "config"
        try:
            command = load_config(config_path)
            stage = "acquire"
            raw = acquire(command)
            stage = "validate"
            data = validate_response(raw)
            content = ("[monitor]\n"
                       f"aws_access_key_id = {data['AccessKeyId']}\n"
                       f"aws_secret_access_key = {data['SecretAccessKey']}\n"
                       f"aws_session_token = {data['SessionToken']}\n")
            stage = "publish"
            atomic_write(directory, "credentials", content)
            status.update(result="success", stage="complete", code=None,
                          last_success_at=timestamp(),
                          credential_expires_at=expiry(data["Expiration"]).isoformat())
        except Exception as error:
            code = str(error) if isinstance(error, RefreshError) else "refresh_failed"
            status.update(result="failure", stage=stage, code=code)
            atomic_write(directory, "status.json", json.dumps(status) + "\n")
            raise RefreshError(code) from None
        # Separate files: a status write failure does not roll back published credentials.
        atomic_write(directory, "status.json", json.dumps(status) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--runtime-directory", type=Path, required=True)
    args = parser.parse_args()
    try:
        refresh(args.config, args.runtime_directory)
    except Exception as error:
        code = str(error) if isinstance(error, RefreshError) else "refresh_failed"
        print(f"monitor credentials: {code}", file=sys.stderr)
        return 1
    print("monitor credentials: refreshed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
