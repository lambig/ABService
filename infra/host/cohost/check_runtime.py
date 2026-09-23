#!/usr/bin/env python3
"""Isolated real-Docker acceptance of the cohost deployment, without AWS access."""
import argparse
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid
from unittest.mock import patch

import deploy
from test_deploy import VALUES, config


def docker(*args):
    return deploy.run("docker", *args)


def unused_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", required=True, help="Locally built production application image")
    args = parser.parse_args()
    os.umask(0o077)
    root = Path(tempfile.mkdtemp(prefix="ab-cohost-check-"))
    name = "ab-cohost-check-" + uuid.uuid4().hex[:12]
    registry = name + "-registry"
    registry_port = unused_port()
    c = config(root / "auth")
    c.update(name=name, port=unused_port())
    auth = Path(c["auth_dir"])
    auth.mkdir(mode=0o755)
    os.chmod(auth, 0o755)
    # Deliberately invalid credentials: prove the process profile is consumable by
    # the real app without exposing an AWS key or granting access to any resource.
    credentials = {"Version": 1, "AccessKeyId": "INVALID_FIXTURE_KEY",
                   "SecretAccessKey": "INVALID_FIXTURE_SECRET", "SessionToken": "INVALID_FIXTURE_TOKEN",
                   "Expiration": "2099-01-01T00:00:00Z"}
    (auth / "credentials.sh").write_text("#!/bin/sh\nprintf '%s' '" + json.dumps(credentials) + "'\n")
    (auth / "config").write_text("[default]\ncredential_process = /run/abservice-auth/credentials.sh\n")
    os.chmod(auth / "credentials.sh", 0o755)
    os.chmod(auth / "config", 0o644)
    state = root / "state-$literal"
    tags = []
    registry_created = False

    def http(path, method="GET", payload=None, origin=True, admin=False):
        headers = {"Content-Type": "application/json"}
        if origin:
            headers["X-Origin-Verify"] = VALUES["app/origin-verify-token"]
        if admin:
            headers["Authorization"] = "Bearer " + VALUES["app/admin-api-key"]
        request = urllib.request.Request(f'http://127.0.0.1:{c["port"]}' + path, method=method, headers=headers,
                                         data=json.dumps(payload).encode() if payload is not None else None)
        try:
            with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(request, timeout=10) as response:
                return response.status, response.read().decode()
        except urllib.error.HTTPError as error:
            return error.code, error.read().decode()

    def service_id(service):
        return deploy.compose(c, state / "candidate.compose.json", "ps", "-q", service).strip()

    def sql(query):
        return docker("exec", service_id("postgres"), "psql", "-U", "postgres", "-d", VALUES["db/name"],
                      "-At", "-v", "ON_ERROR_STOP=1", "-c", query).strip()

    def publish(local, tag):
        target = f"127.0.0.1:{registry_port}/{name}:{tag}"
        docker("tag", local, target)
        tags.append(target)
        docker("push", target)
        refs = json.loads(docker("image", "inspect", target))[0]["RepoDigests"]
        return next(ref for ref in refs if ref.startswith(f"127.0.0.1:{registry_port}/{name}@"))

    try:
        # All resources have unguessable task names; no shared stack is stopped/pruned.
        docker("run", "-d", "--name", registry, "--label", "abservice.cohost-test=" + name,
               "-p", f"127.0.0.1:{registry_port}:5000", "registry:2")
        registry_created = True
        for _ in range(60):
            try:
                with urllib.request.urlopen(f"http://127.0.0.1:{registry_port}/v2/", timeout=2):
                    break
            except Exception:
                time.sleep(1)
        else:
            raise AssertionError("Fixture registry did not start")
        first = publish(args.image, "first")
        docker("pull", "postgres:15-alpine")
        c["postgres_image"] = publish("postgres:15-alpine", "database")
        # A distinct but equivalent application image exercises real container replacement.
        (root / "Dockerfile").write_text(f"FROM {args.image}\nLABEL cohost.fixture={name}\n")
        second_tag = name + ":second"
        docker("build", "-t", second_tag, str(root))
        tags.append(second_tag)
        second = publish(second_tag, "second")
        assert first != second

        with patch.object(deploy, "parameters", return_value=VALUES):
            deploy.deploy(c, first, "1" * 40, state, initialize=True)
            initial_db = service_id("postgres")
            assert not json.loads(docker("inspect", initial_db))[0]["HostConfig"]["PortBindings"]
            assert sql("select rolsuper or rolcreatedb or rolcreaterole from pg_roles where rolname='fixture_app'") == "f"
            assert http("/api/v1/site-contents", origin=False)[0] == 403
            payload = {"content": "cohost-persisted-fixture", "contentFormat": "PLAIN_TEXT"}
            assert http("/api/v1/site-contents/cohost.test", "PUT", payload)[0] == 401
            assert http("/api/v1/site-contents/cohost.test", "PUT", payload, admin=True)[0] == 200
            before = json.loads(http("/api/v1/site-contents")[1])
            assert "cohost-persisted-fixture" in json.dumps(before)
            # Also proves $/quotes/newline DB passwords reach both PostgreSQL and app unchanged.
            assert sql("select count(*) from flyway_schema_history where success") != "0"
            print("initial boot, migration, restricted role, authentication and write passed", flush=True)

            deploy.compose(c, state / "candidate.compose.json", "restart")
            deploy.deploy(c, first, "1" * 40, state)
            assert json.loads(http("/api/v1/site-contents")[1]) == before
            first_backend = service_id("backend")
            deploy.deploy(c, second, "2" * 40, state)
            assert service_id("backend") != first_backend
            assert service_id("postgres") == initial_db
            assert json.loads(http("/api/v1/site-contents")[1]) == before
            assert deploy.read_json(state / "previous.json")["image"] == first
            print("restart and distinct-image deployment preserved DB/data and previous version", flush=True)

            # A real non-app image must never become a successful release.
            (root / "Dockerfile").write_text("FROM postgres:15-alpine\nENTRYPOINT [\"false\"]\n")
            broken_tag = name + ":broken"
            docker("build", "-t", broken_tag, str(root))
            tags.append(broken_tag)
            broken = publish(broken_tag, "broken")
            current = (state / "current.json").read_bytes()
            try:
                deploy.deploy(c, broken, "3" * 40, state)
            except deploy.DeployError:
                pass
            else:
                raise AssertionError("Unhealthy image was accepted")
            assert (state / "current.json").read_bytes() == current
            assert deploy.read_json(state / "attempt.json")["status"] == "failed"
            deploy.deploy(c, first, "1" * 40, state)
            assert json.loads(http("/api/v1/site-contents")[1]) == before
            print("failed deployment stayed failed; manual redeploy restored service with data intact", flush=True)

            deploy.compose(c, state / "candidate.compose.json", "down", "--volumes")
            docker("volume", "inspect", name + "-postgres")
            deploy.deploy(c, first, "1" * 40, state)
            assert json.loads(http("/api/v1/site-contents")[1]) == before
            for service in ("backend", "postgres"):
                assert not json.loads(docker("inspect", service_id(service)))[0]["State"]["OOMKilled"]
            print("external volume survived stack removal; recreated containers read saved data", flush=True)
    finally:
        try:
            if (state / "candidate.compose.json").exists():
                deploy.compose(c, state / "candidate.compose.json", "down", "--remove-orphans")
            volumes = docker("volume", "ls", "--filter", "label=abservice.cohost=" + name, "--format", "{{.Name}}").splitlines()
            for volume in volumes:
                assert volume == name + "-postgres"
                docker("volume", "rm", volume)
            if registry_created:
                docker("rm", "-fv", registry)
            for tag in reversed(tags):
                subprocess.run(["docker", "image", "rm", tag], capture_output=True)
        finally:
            shutil.rmtree(root)


if __name__ == "__main__":
    main()
