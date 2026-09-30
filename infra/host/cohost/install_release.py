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
import uuid
import zipfile
import zlib

FILES = ("deploy.py", "init-db.sh")


def migration_manifest(jar):
    """Read the shipped SQL, never a checkout or an image-supplied manifest."""
    migrations = {}
    with zipfile.ZipFile(jar) as archive:
        for entry in archive.infolist():
            if not entry.filename.startswith("db/migration/") or entry.is_dir():
                continue
            match = re.fullmatch(r"db/migration/V([1-9][0-9]*)__([^/]+)\.sql", entry.filename)
            if not match or entry.file_size > 4 * 1024 * 1024:
                raise ValueError("Unsupported migration layout")
            version = match[1]
            if version in migrations:
                raise ValueError("Duplicate migration version")
            # Flyway SQL checksum: UTF-8, optional leading BOM, CRC32 without
            # CR/LF separators, interpreted as a signed Java int. Runtime tests
            # compare this with history written by the real bundled Flyway.
            sql = archive.read(entry).decode("utf-8-sig")
            checksum = zlib.crc32(sql.replace("\r", "").replace("\n", "").encode("utf-8"))
            if checksum >= 2 ** 31:
                checksum -= 2 ** 32
            migrations[version] = {"script": entry.filename.rsplit("/", 1)[1],
                                   "description": match[2].replace("_", " ")[:200], "checksum": checksum}
    if not migrations:
        raise ValueError("Candidate has no supported migrations")
    return migrations


def check_history(migrations, history):
    if not isinstance(history, list) or not history:
        raise ValueError("No applied migration history")
    applied = set()
    for row in history:
        version = row.get("version")
        candidate = migrations.get(version)
        if (not candidate or version in applied or row.get("success") is not True
                or row.get("type") != "SQL"
                # Quarkus records the classpath-relative name; standalone Flyway
                # can record only the filename. No other path aliases are accepted.
                or row.get("script") not in (candidate["script"], "db/migration/" + candidate["script"])
                or any(row.get(key) != candidate[key] for key in ("description", "checksum"))):
            raise ValueError("Candidate migrations do not match the applied database history")
        applied.add(version)
    # Default Flyway out-of-order=false would skip an unapplied older migration.
    if any(int(version) < max(map(int, applied)) for version in migrations.keys() - applied):
        raise ValueError("Candidate contains an unapplied older migration")


def captured(args, execute=subprocess.run):
    result = execute(args, capture_output=True, timeout=180)
    if result.returncode:
        raise RuntimeError("Migration preflight command failed; inspect host privately")
    return result.stdout.decode()


def image_migrations(image, execute=subprocess.run):
    """Extract from the digest already pulled/verified by the authenticated runner."""
    with tempfile.TemporaryDirectory(prefix="cohost-migration-") as directory:
        jar = Path(directory) / "app.jar"
        container = "cohost-migration-" + uuid.uuid4().hex
        try:
            # Never start the candidate, mount production storage or pass credentials.
            captured(["docker", "create", "--name", container, "--network", "none", image], execute)
            captured(["docker", "cp", container + ":/deployments/app.jar", str(jar)], execute)
            return migration_manifest(jar)
        finally:
            captured(["docker", "rm", "-fv", container], execute)


def preflight(migrations, state, execute=subprocess.run):
    """Read-only history gate before replacing scripts/config or stopping the app.

    This is deliberately stricter than Flyway ignore patterns. It is not a proof
    of DDL/data compatibility or a substitute for isolated upgrade testing.
    """
    current = json.loads((state / "current.json").read_text())
    name = current["name"]
    database = current["compose"]["services"]["postgres"]["environment"]["POSTGRES_DB"]
    if not re.fullmatch(r"[a-z][a-z0-9-]{0,39}", name) or not re.fullmatch(r"[a-z][a-z0-9_]{0,62}", database):
        raise ValueError("Unsupported current database identity")
    with tempfile.TemporaryDirectory(prefix="cohost-migration-") as directory:
        directory = Path(directory)
        compose = directory / "current.compose.json"
        replace(compose, json.dumps(current["compose"]).encode())
        query = ("BEGIN READ ONLY; SET LOCAL statement_timeout='10s'; "
                 "SELECT json_agg(h ORDER BY installed_rank) FROM "
                 "(SELECT installed_rank, version, description, type, script, checksum, success "
                 "FROM public.flyway_schema_history) h; COMMIT;")
        output = captured(["docker", "compose", "--env-file", "/dev/null", "-p", name, "-f", str(compose),
                          "exec", "-T", "postgres", "psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1",
                          "-U", "postgres", "-d", database, "-c", query], execute)
        check_history(migrations, json.loads(output))


def validate(payload):
    if set(payload) != {"source", "image", "files", "migrations"}:
        raise ValueError("Invalid release fields")
    if not re.fullmatch(r"[a-f0-9]{40}", payload["source"]):
        raise ValueError("Invalid source")
    if not re.fullmatch(r"[0-9]{12}\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com/[a-z0-9][a-z0-9._/-]*@sha256:[a-f0-9]{64}", payload["image"]):
        raise ValueError("Invalid image")
    if set(payload["files"]) != set(FILES):
        raise ValueError("Invalid file set")
    migrations = payload["migrations"]
    if not isinstance(migrations, dict) or not migrations:
        raise ValueError("Missing image migration manifest")
    for version, item in migrations.items():
        if (not re.fullmatch(r"[1-9][0-9]*", version) or not isinstance(item, dict)
                or set(item) != {"script", "description", "checksum"}
                or not re.fullmatch(r"V" + version + r"__[^/]+\.sql", item["script"])
                or item["description"] != item["script"].split("__", 1)[1][:-4].replace("_", " ")[:200]
                or type(item["checksum"]) is not int or not -(2 ** 31) <= item["checksum"] < 2 ** 31):
            raise ValueError("Invalid image migration manifest")
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
        preflight(payload["migrations"], state, execute)
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
