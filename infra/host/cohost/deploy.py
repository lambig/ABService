#!/usr/bin/env python3
"""Opt-in Linux host deployment; never invokes Terraform or changes DNS."""
import argparse
import datetime
import fcntl
import ipaddress
import json
import os
from pathlib import Path
import re
import subprocess
import sys


class DeployError(Exception):
    pass


def run(*args, timeout=300):
    # AWS values, Docker config and diagnostic output may contain secrets.
    # Never include command output or arguments in an exception/CI log.
    try:
        result = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
    except (OSError, subprocess.TimeoutExpired) as error:
        raise DeployError(f"{args[0]} could not finish; inspect the host privately") from error
    if result.returncode:
        raise DeployError(f"{args[0]} failed (exit {result.returncode}); inspect the host privately")
    return result.stdout


def write_json(path, value):
    temporary = path.with_suffix(".tmp")
    with temporary.open("w", encoding="utf-8") as stream:
        os.chmod(temporary, 0o600)
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
        stream.flush()
        os.fsync(stream.fileno())
    temporary.replace(path)


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def check_digest(value):
    if not isinstance(value, str) or not re.fullmatch(r"[a-zA-Z0-9._:/-]+@sha256:[0-9a-f]{64}", value):
        raise DeployError("Images must be full repository@sha256 references")


def validate(config, image, source):
    required = {"name", "region", "parameter_prefix", "postgres_image", "auth_dir", "bind_address", "port"}
    if set(config) != required:
        raise DeployError("Config must contain exactly the documented fields")
    if not re.fullmatch(r"[a-z][a-z0-9-]{0,39}", config["name"]):
        raise DeployError("Invalid Compose project name")
    if not re.fullmatch(r"[a-z]{2}(?:-[a-z]+)+-[0-9]", config["region"]):
        raise DeployError("Invalid AWS region")
    if not re.fullmatch(r"/[a-zA-Z0-9_./-]+", config["parameter_prefix"]) or config["parameter_prefix"].endswith("/"):
        raise DeployError("Invalid Parameter Store prefix")
    ipaddress.IPv4Address(config["bind_address"])
    if type(config["port"]) is not int or not 1024 <= config["port"] <= 65535:
        raise DeployError("Invalid backend port")
    check_digest(image)
    check_digest(config["postgres_image"])
    if not re.fullmatch(r"[0-9a-f]{40}", source):
        raise DeployError("Source must be the full verified commit SHA")
    auth = Path(config["auth_dir"])
    if not auth.is_absolute() or not auth.is_dir() or not (auth / "config").is_file():
        raise DeployError("auth_dir must contain the prepared AWS profile config")


def parameters(config):
    values = {}
    for key in ("db/name", "db/username", "db/password", "db/admin-password",
                "app/admin-api-key", "app/origin-verify-token", "assets/bucket"):
        response = json.loads(run("aws", "ssm", "get-parameter", "--region", config["region"],
                                  "--name", config["parameter_prefix"] + "/" + key,
                                  "--with-decryption", "--output", "json"))
        value = response["Parameter"]["Value"]
        if not value or "\x00" in value:
            raise DeployError("Missing or invalid deployment parameter")
        values[key] = value
    for key in ("db/name", "db/username"):
        if not re.fullmatch(r"[a-z][a-z0-9_]{0,62}", values[key]) or values[key].startswith("pg_"):
            raise DeployError("DB identifiers must be simple lowercase identifiers")
        if values[key] in ("postgres", "template0", "template1"):
            raise DeployError("Use a dedicated application database and non-admin role")
    if values["db/password"] == values["db/admin-password"]:
        raise DeployError("Application and database administrator passwords must differ")
    return values


def compose_config(config, image, values):
    logging = {"driver": "local", "options": {"max-size": "10m", "max-file": "3"}}
    backend_env = {
        "DB_HOST": "postgres", "DB_PORT": "5432", "DB_NAME": values["db/name"],
        "DB_USERNAME": values["db/username"], "DB_PASSWORD": values["db/password"],
        "ADMIN_API_KEY": values["app/admin-api-key"], "ORIGIN_VERIFY_TOKEN": values["app/origin-verify-token"],
        "ASSETS_BUCKET": values["assets/bucket"], "AWS_REGION": config["region"],
        "AWS_PROFILE": "default", "AWS_CONFIG_FILE": "/run/abservice-auth/config",
        "AWS_SHARED_CREDENTIALS_FILE": "/dev/null", "AWS_EC2_METADATA_DISABLED": "true",
        "JAVA_TOOL_OPTIONS": "-Xms128m -Xmx384m -XX:MaxMetaspaceSize=192m -XX:ActiveProcessorCount=2",
        "QUARKUS_DATASOURCE_JDBC_MAX_SIZE": "8", "QUARKUS_DATASOURCE_REACTIVE_MAX_SIZE": "12",
    }
    model = {
        "services": {
            "backend": {
                "image": image, "restart": "unless-stopped", "mem_limit": "768m", "memswap_limit": "768m",
                "cpus": 1, "environment": backend_env, "logging": logging,
                "ports": [f'{config["bind_address"]}:{config["port"]}:8080'],
                "networks": ["egress", "database"],
                "volumes": [{"type": "bind", "source": config["auth_dir"], "target": "/run/abservice-auth",
                             "read_only": True, "bind": {"create_host_path": False}}],
                "depends_on": {"postgres": {"condition": "service_healthy"}},
                "healthcheck": {"test": ["CMD", "curl", "-fsS", "http://localhost:8080/q/health/ready"],
                                "interval": "5s", "timeout": "5s", "retries": 12, "start_period": "60s"},
            },
            "postgres": {
                "image": config["postgres_image"], "restart": "unless-stopped", "mem_limit": "512m",
                "memswap_limit": "512m", "cpus": 1, "logging": logging, "networks": ["database"],
                "environment": {"POSTGRES_USER": "postgres", "POSTGRES_DB": values["db/name"],
                                "POSTGRES_PASSWORD": values["db/admin-password"],
                                "APP_DB_USER": values["db/username"], "APP_DB_PASSWORD": values["db/password"],
                                "POSTGRES_INITDB_ARGS": "--encoding=UTF-8 --locale=C"},
                "command": ["postgres", "-c", "shared_buffers=128MB", "-c", "max_connections=40",
                            "-c", "work_mem=4MB", "-c", "maintenance_work_mem=64MB"],
                "volumes": [{"type": "volume", "source": "data", "target": "/var/lib/postgresql/data"},
                            {"type": "bind", "source": str(Path(__file__).with_name("init-db.sh").resolve()),
                             "target": "/docker-entrypoint-initdb.d/10-app.sh", "read_only": True,
                             "bind": {"create_host_path": False}}],
                "healthcheck": {"test": ["CMD", "pg_isready", "-h", "127.0.0.1", "-U", "postgres", "-d", values["db/name"]],
                                "interval": "5s", "timeout": "5s", "retries": 12},
            },
        },
        "networks": {"egress": {}, "database": {"internal": True}},
        "volumes": {"data": {"external": True, "name": config["name"] + "-postgres"}},
    }
    # Compose interpolates even JSON strings. Preserve literal $, quotes and newlines
    # in Parameter Store values; no secrets are evaluated as shell/Compose syntax.
    return escape_compose(model)


def escape_compose(value):
    if isinstance(value, str):
        return value.replace("$", "$$")
    if isinstance(value, list):
        return [escape_compose(item) for item in value]
    if isinstance(value, dict):
        return {key: escape_compose(item) for key, item in value.items()}
    return value


def compose(config, path, *args):
    # Explicit file/project and empty env-file avoid another checkout's .env.
    return run("docker", "compose", "--env-file", "/dev/null", "-p", config["name"], "-f", str(path), *args)


def deploy(config, image, source, state_dir, initialize=False):
    validate(config, image, source)
    state_dir = Path(state_dir).resolve()
    state_dir.mkdir(mode=0o700, parents=True, exist_ok=True)
    if state_dir.stat().st_uid != os.geteuid() or state_dir.stat().st_mode & 0o077:
        raise DeployError("State directory must be owned by the operator and mode 0700")
    with (state_dir / "deploy.lock").open("w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        attempt = {"source": source, "image": image, "status": "preparing",
                   "time": datetime.datetime.now(datetime.timezone.utc).isoformat()}
        write_json(state_dir / "attempt.json", attempt)
        try:
            apply_locked(config, image, source, state_dir, initialize, attempt)
        except (DeployError, OSError, ValueError, KeyError, TypeError):
            attempt["status"] = "failed"
            write_json(state_dir / "attempt.json", attempt)
            raise


def apply_locked(config, image, source, state_dir, initialize, attempt):
    current_path = state_dir / "current.json"
    current = read_json(current_path) if current_path.exists() else None
    if current and initialize:
        raise DeployError("Already deployed; initialization is never an update operation")
    model = compose_config(config, image, parameters(config))
    # Keep this mount path stable when the checked-out deployment version changes.
    init_file = state_dir / "init-db.sh"
    init_file.write_bytes(Path(__file__).with_name("init-db.sh").read_bytes())
    os.chmod(init_file, 0o644)  # The unprivileged postgres entrypoint must read it.
    model["services"]["postgres"]["volumes"][1]["source"] = escape_compose(str(init_file))
    if current:
        # This command updates only the app. Changing DB identity/password/image
        # on an existing volume requires a separate, explicit DB operation.
        old = current["compose"]
        if (current["name"] != config["name"] or old["services"]["postgres"] != model["services"]["postgres"]
                or old["volumes"] != model["volumes"]):
            raise DeployError("Database configuration changed; perform a planned DB operation first")
    volume = config["name"] + "-postgres"
    if initialize:
        # inspect failure alone cannot distinguish an absent volume from a broken daemon.
        names = run("docker", "volume", "ls", "--format", "{{.Name}}").splitlines()
        if volume in names:
            raise DeployError("Refusing to initialize an existing volume")
        run("docker", "volume", "create", "--label", "abservice.cohost=" + config["name"], volume)
    info = json.loads(run("docker", "volume", "inspect", volume))[0]
    if (info.get("Labels") or {}).get("abservice.cohost") != config["name"]:
        raise DeployError("Database volume ownership label does not match")
    candidate = state_dir / "candidate.compose.json"
    write_json(candidate, model)
    compose(config, candidate, "config", "--quiet")
    for ref in (image, config["postgres_image"]):
        run("docker", "pull", ref)
        architecture = run("docker", "image", "inspect", ref, "--format", "{{.Architecture}}").strip()
        host = run("docker", "info", "--format", "{{.Architecture}}").strip()
        host = {"x86_64": "amd64", "aarch64": "arm64"}.get(host, host)
        if architecture != host:
            raise DeployError("Image architecture does not match the Docker host")
    attempt["status"] = "starting"
    write_json(state_dir / "attempt.json", attempt)
    compose(config, candidate, "up", "-d", "--wait", "--wait-timeout", "240")
    backend_id = compose(config, candidate, "ps", "-q", "backend").strip()
    actual = run("docker", "inspect", backend_id, "--format", "{{.Image}}").strip()
    expected = run("docker", "image", "inspect", image, "--format", "{{.Id}}").strip()
    if actual != expected:
        raise DeployError("Running backend differs from the requested image")
    if current and current["image"] != image:
        write_json(state_dir / "previous.json", current)
    write_json(current_path, {"name": config["name"], "source": source, "image": image, "compose": model})
    attempt["status"] = "healthy"
    write_json(state_dir / "attempt.json", attempt)
    # Never prune images or remove volumes; an older app may be needed for manual recovery.
    print(f"healthy backend image: {image}; source: {source}", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", required=True)
    parser.add_argument("--state-dir", required=True)
    parser.add_argument("--image", required=True)
    parser.add_argument("--source", required=True)
    parser.add_argument("--initialize", action="store_true")
    args = parser.parse_args()
    os.umask(0o077)
    try:
        deploy(read_json(Path(args.config)), args.image, args.source, args.state_dir, args.initialize)
    except (DeployError, OSError, ValueError, KeyError, TypeError):
        # Generic on purpose: a parser exception can contain secret parameter values.
        print("Deployment failed. Inspect the protected host state and service status; no automatic rollback was performed.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
