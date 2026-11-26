"""Bounded SSM transport for the opt-in cohost deployment target."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import re
import runpy
import subprocess
import sys
import tempfile
import time

DELIVERY_TIMEOUT = 60
EXECUTION_TIMEOUT = 660
POLL_SECONDS = 780
FILES = ("deploy.py", "init-db.sh")
TRANSIENT = {"Pending", "InProgress", "Delayed", "Cancelling"}


def command(args):
    result = subprocess.run(args, capture_output=True, text=True, timeout=60)
    if result.returncode:
        # Only this specific propagation delay may be retried. Authorization and
        # transport errors are never represented as a successful/absent command.
        if args[:3] == ["aws", "ssm", "get-command-invocation"] and "(InvocationDoesNotExist)" in result.stderr:
            return {"Status": "Pending"}
        raise RuntimeError("Command failed; sensitive output suppressed")
    return json.loads(result.stdout)


def parameters(checkout, source, image):
    if not re.fullmatch(r"[a-f0-9]{40}", source):
        raise ValueError("Use a full source SHA")
    if not re.fullmatch(r"[0-9]{12}\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com/[a-z0-9][a-z0-9._/-]*@sha256:[a-f0-9]{64}", image):
        raise ValueError("Use an ECR image digest")
    files = {}
    for name in FILES:
        result = subprocess.run(["git", "-C", str(checkout), "show", f"{source}:infra/host/cohost/{name}"],
                                capture_output=True, timeout=30)
        if result.returncode or not result.stdout:
            raise ValueError("Candidate lacks the cohost deployment files")
        files[name] = {"content": base64.b64encode(result.stdout).decode(),
                       "sha256": hashlib.sha256(result.stdout).hexdigest()}
    installer = Path(__file__).resolve().parents[1] / "host/cohost/install_release.py"
    migrations = runpy.run_path(str(installer))["image_migrations"](image)
    payload = base64.b64encode(json.dumps({"source": source, "image": image, "files": files,
                                         "migrations": migrations}).encode()).decode()
    code = base64.b64encode(installer.read_bytes()).decode()
    # Both substitutions are base64, never shell fragments supplied by an operator.
    # timeout terminates the remote process group before the SSM execution limit.
    script = f"import base64; exec(compile(base64.b64decode('{code}'), '<release-installer>', 'exec'))"
    result = {"commands": [f'timeout --kill-after=10s 630s python3 -c "{script}" {payload}'],
              "executionTimeout": [str(EXECUTION_TIMEOUT)]}
    if len(json.dumps(result).encode()) > 92160:
        raise ValueError("SSM payload exceeds the delivery budget")
    return result


def deliver(node, source, params, aws=command, clock=time.monotonic, sleep=time.sleep):
    if not re.fullmatch(r"mi-[a-f0-9]{17}", node):
        raise ValueError("Use an explicit managed-node ID")
    with tempfile.TemporaryDirectory(prefix="cohost-delivery-") as directory:
        path = Path(directory) / "parameters.json"
        path.write_text(json.dumps(params))
        path.chmod(0o600)
        sent = aws(["aws", "ssm", "send-command", "--instance-ids", node,
                    "--document-name", "AWS-RunShellScript", "--comment", "Deploy sha-" + source,
                    "--timeout-seconds", str(DELIVERY_TIMEOUT), "--parameters", "file://" + str(path),
                    "--query", "Command.CommandId", "--output", "json", "--no-cli-pager"])
    if not isinstance(sent, str) or not re.fullmatch(r"[a-f0-9-]{36}", sent):
        raise RuntimeError("Invalid SSM command ID")
    print("SSM command id: " + sent, flush=True)
    deadline = clock() + POLL_SECONDS
    while clock() < deadline:
        status = aws(["aws", "ssm", "get-command-invocation", "--command-id", sent,
                      "--instance-id", node, "--query", "{Status:Status,ResponseCode:ResponseCode}",
                      "--output", "json", "--no-cli-pager"])
        if status.get("Status") == "Success" and status.get("ResponseCode") == 0:
            print("SSM verified host update succeeded", flush=True)
            return
        if status.get("Status") not in TRANSIENT:
            raise RuntimeError("SSM update did not succeed; inspect host privately")
        sleep(5)
    raise RuntimeError("SSM outcome unconfirmed; inspect command/host before retrying")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkout", required=True, type=Path)
    parser.add_argument("--source", required=True)
    parser.add_argument("--image", required=True)
    parser.add_argument("--node", required=True)
    args = parser.parse_args()
    try:
        deliver(args.node, args.source, parameters(args.checkout, args.source, args.image))
    except Exception:
        print("Cohost delivery failed; no frontend delivery is permitted", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
