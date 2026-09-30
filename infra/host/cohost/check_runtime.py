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
import urllib.parse
import uuid
import zipfile
from unittest.mock import patch

import deploy
import install_release
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
    audio = root / "private-audio"

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
            status, body = http("/api/v1/assets/upload-url", "POST", {"contentType": "image/png"}, admin=True)
            assert status == 200, "Presign did not resolve the process profile"
            signed = urllib.parse.urlsplit(json.loads(body)["uploadUrl"])
            query = urllib.parse.parse_qs(signed.query)
            assert query["X-Amz-Credential"][0].split("/")[0] == credentials["AccessKeyId"]
            assert query["X-Amz-Security-Token"] == [credentials["SessionToken"]]
            assert query["X-Amz-Signature"][0]
            # Do not request or log the URL: signing is local and the key is invalid.
            print("presign consumed the fixture process credentials and session token", flush=True)
            before = json.loads(http("/api/v1/site-contents")[1])
            assert "cohost-persisted-fixture" in json.dumps(before)
            # Also proves $/quotes/newline DB passwords reach both PostgreSQL and app unchanged.
            assert sql("select count(*) from flyway_schema_history where success") != "0"
            print("initial boot, migration, restricted role, authentication and write passed", flush=True)
            first_migrations = install_release.image_migrations(first)
            install_release.preflight(first_migrations, state)

            for other_state in (root / "mistyped-state", root / "copied-state"):
                if other_state.name == "copied-state":
                    shutil.copytree(state, other_state)
                try:
                    deploy.deploy(c, second, "2" * 40, other_state)
                except deploy.DeployError:
                    pass
                else:
                    raise AssertionError("Another state directory adopted the running database")
                assert service_id("postgres") == initial_db
                assert json.loads(http("/api/v1/site-contents")[1]) == before
            print("mistyped/copied state directories rejected without changing the running database", flush=True)

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

            audio_api = "/api/v1/admin/private-audio/registrations"
            assert http(audio_api, "POST", admin=True)[0] == 404
            audio.mkdir(mode=0o700)
            # CI's host UID can differ from the fixed application UID. Change only
            # this disposable directory, never a production path or its contents.
            docker("run", "--rm", "--user", "0:0", "--entrypoint", "chown",
                   "-v", str(audio) + ":/audio", args.image, "1000:1000", "/audio")
            c["private_audio"] = {"enabled": True, "temporary_dir": str(audio)}
            with patch.object(deploy, "parameters", return_value={**VALUES, "private-audio/bucket": "invalid-audio-fixture"}):
                deploy.deploy(c, second, "2" * 40, state)
                assert service_id("postgres") == initial_db
                status, body = http(audio_api, "POST", admin=True)
                assert status == 201, "Private audio reservation did not use opt-in configuration"
                audio_id = json.loads(body)["audioId"]
                assert http(audio_api, "POST")[0] == 401
                # Runtime initialization acquires the dedicated directory as UID 1000.
                docker("exec", service_id("backend"), "sh", "-c",
                       "test -w /var/lib/abservice/private-audio && test -f /var/lib/abservice/private-audio/.audio-owner.lock")
                deploy.compose(c, state / "candidate.compose.json", "restart", "backend")
                deploy.deploy(c, second, "2" * 40, state)
                assert http(audio_api + "/" + audio_id, admin=True)[0] == 200
            c["private_audio"]["enabled"] = False
            deploy.deploy(c, second, "2" * 40, state)
            assert http(audio_api, "POST", admin=True)[0] == 404
            assert json.loads(http("/api/v1/site-contents")[1]) == before
            assert service_id("postgres") == initial_db
            del c["private_audio"]
            print("private audio opt-in, writable storage, restart and disable preserved DB/data", flush=True)

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

            # Real future migration, applied by the production image's Flyway.
            # No hand-written/edited schema-history rows and no ignore-future override.
            extraction = name + "-extract"
            try:
                docker("create", "--name", extraction, "--network", "none", args.image)
                docker("cp", extraction + ":/deployments/app.jar", str(root / "app.jar"))
            finally:
                docker("rm", "-fv", extraction)
            versions = install_release.migration_manifest(root / "app.jar")
            future_version = max(map(int, versions)) + 1
            with zipfile.ZipFile(root / "app.jar", "a") as archive:
                archive.writestr(f"db/migration/V{future_version}__Cohost_future_fixture.sql",
                                 "CREATE TABLE cohost_future_fixture (id integer PRIMARY KEY);\n")
            (root / "Dockerfile").write_text(f"FROM {args.image}\nCOPY --chown=1000:1000 app.jar /deployments/app.jar\n")
            future_tag = name + ":future"
            docker("build", "-t", future_tag, str(root))
            tags.append(future_tag)
            future = publish(future_tag, "future")
            future_migrations = install_release.image_migrations(future)
            install_release.preflight(future_migrations, state)  # Forward migration is allowed.
            deploy.deploy(c, future, "4" * 40, state)
            install_release.preflight(future_migrations, state)  # Real Flyway checksums match.
            healthy_backend = service_id("backend")
            healthy_db = service_id("postgres")
            saved_state = {p: p.read_bytes() for p in state.iterdir() if p.is_file()}
            history = sql("select json_agg(h order by installed_rank) from flyway_schema_history h")
            try:
                install_release.preflight(first_migrations, state)
            except ValueError as error:
                assert "do not match" in str(error)
            else:
                raise AssertionError("Future database was accepted by the older image")
            for path, content in saved_state.items():
                assert path.read_bytes() == content
            assert service_id("backend") == healthy_backend
            assert service_id("postgres") == healthy_db
            assert json.loads(http("/api/v1/site-contents")[1]) == before
            assert sql("select json_agg(h order by installed_rank) from flyway_schema_history h") == history

            # Independently show the older real app fails startup on that schema.
            # One-off container shares only this disposable DB, with no published port.
            probe_model = deploy.read_json(state / "current.json")["compose"]
            probe_model["services"]["backend"]["image"] = first
            probe_file = root / "old-app.compose.json"
            deploy.write_json(probe_file, probe_model)
            probe = name + "-old-app"
            try:
                result = subprocess.run(["docker", "compose", "--env-file", "/dev/null", "-p", name,
                                         "-f", str(probe_file), "run", "--no-deps", "--name", probe, "backend"],
                                        capture_output=True, timeout=90)
                logs = result.stdout + result.stderr
                assert result.returncode != 0
                assert b"Detected applied migration not resolved locally" in logs
            finally:
                docker("rm", "-fv", probe)
            assert service_id("backend") == healthy_backend
            assert json.loads(http("/api/v1/site-contents")[1]) == before
            assert sql("select json_agg(h order by installed_rank) from flyway_schema_history h") == history
            print("future migration: old real app failed validation; preflight rejected it without changing service/data/history", flush=True)
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
            if audio.exists():
                # Only the random, test-owned fixture tree is reclaimed.
                docker("run", "--rm", "--user", "0:0", "--entrypoint", "chown",
                       "-v", str(audio) + ":/audio", args.image, "-R", f"{os.getuid()}:{os.getgid()}", "/audio")
            shutil.rmtree(root)


if __name__ == "__main__":
    main()
