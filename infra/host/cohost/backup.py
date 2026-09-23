#!/usr/bin/env python3
"""Save a consistent DB dump and retained S3 asset versions; never restore over a DB."""
import argparse
import base64
import datetime as dt
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

import deploy


class BackupError(Exception):
    pass


def now():
    return dt.datetime.now(dt.timezone.utc).isoformat()


def aws(config, operation, *args):
    output = deploy.run('aws', 's3api', operation, *args, '--region', config['region'],
                        '--output', 'json', '--no-cli-pager', timeout=900)
    return json.loads(output) if output.strip() else {}


def validate(config):
    if set(config) != {'region', 'bucket', 'prefix'}:
        raise BackupError('Unexpected backup configuration fields')
    if not re.fullmatch(r'[a-z]{2}(?:-[a-z]+)+-[0-9]', config['region']):
        raise BackupError('Invalid region')
    if not re.fullmatch(r'[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]', config['bucket']):
        raise BackupError('Invalid bucket')
    if not re.fullmatch(r'[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*', config['prefix']):
        raise BackupError('Invalid prefix')


def checksum(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def put(config, key, path):
    size, digest = path.stat().st_size, checksum(path)
    # Deliberately bounded to one PutObject. A larger DB requires a reviewed multipart path.
    if not 0 < size <= 5_000_000_000:
        raise BackupError('Artifact is empty or exceeds the single-upload limit')
    response = aws(config, 'put-object', '--bucket', config['bucket'], '--key', key,
                   '--body', str(path), '--if-none-match', '*', '--server-side-encryption', 'AES256',
                   '--checksum-algorithm', 'SHA256', '--checksum-sha256', base64.b64encode(bytes.fromhex(digest)).decode())
    if not response.get('VersionId') or response['VersionId'] == 'null':
        raise BackupError('Backup destination must keep object versions')
    return {'key': key, 'versionId': response['VersionId'], 'bytes': size, 'sha256': digest}


def preflight(config, assets_bucket):
    if assets_bucket == config['bucket']:
        raise BackupError('Use separate asset and backup buckets')
    for bucket in (assets_bucket, config['bucket']):
        if aws(config, 'get-bucket-versioning', '--bucket', bucket).get('Status') != 'Enabled':
            raise BackupError('Bucket versioning is required')
        block = aws(config, 'get-public-access-block', '--bucket', bucket)['PublicAccessBlockConfiguration']
        if not all(block.get(k) for k in ('BlockPublicAcls', 'IgnorePublicAcls', 'BlockPublicPolicy', 'RestrictPublicBuckets')):
            raise BackupError('Bucket public access block is required')
    # The application publishes assets once; all published versions must remain available.
    # Require an explicit lifecycle document (the existing pending/ cleanup satisfies this).
    rules = aws(config, 'get-bucket-lifecycle-configuration', '--bucket', assets_bucket)['Rules']
    for rule in rules:
        if rule.get('Status') != 'Enabled':
            continue
        prefix = rule.get('Filter', {}).get('Prefix', rule.get('Prefix', ''))
        destructive = any(k in rule for k in ('Expiration', 'NoncurrentVersionExpiration', 'Transitions', 'NoncurrentVersionTransitions'))
        if destructive and (not prefix or 'assets/'.startswith(prefix) or prefix.startswith('assets/')):
            raise BackupError('Published asset versions must not expire or transition')


def inventory(config, bucket):
    listed = aws(config, 'list-object-versions', '--bucket', bucket, '--prefix', 'assets/')
    versions = [{k: obj[k] for k in ('Key', 'VersionId', 'ETag', 'Size', 'LastModified', 'IsLatest')}
                for obj in listed.get('Versions', [])]
    if any(not v['Key'].startswith('assets/') or not v['VersionId'] for v in versions):
        raise BackupError('Invalid asset inventory')
    return {'bucket': bucket, 'prefix': 'assets/', 'versions': versions,
            'deleteMarkers': [{k: obj[k] for k in ('Key', 'VersionId', 'LastModified', 'IsLatest')}
                              for obj in listed.get('DeleteMarkers', [])]}


def dump_database(cid, database, path):
    with path.open('wb') as stream:
        os.fchmod(stream.fileno(), 0o600)
        result = subprocess.run(['docker', 'exec', cid, 'pg_dump', '-U', 'postgres', '-d', database,
                                 '-Fc', '--no-owner', '--no-acl'], stdout=stream, stderr=subprocess.PIPE, timeout=900)
    if result.returncode or path.stat().st_size == 0:
        raise BackupError('Database dump failed')
    with path.open('rb') as stream:
        checked = subprocess.run(['docker', 'exec', '-i', cid, 'pg_restore', '-l'], stdin=stream,
                                 capture_output=True, timeout=120)
    if checked.returncode or b'TABLE DATA' not in checked.stdout or b'flyway_schema_history' not in checked.stdout:
        raise BackupError('Database archive is not readable')


def create(config, state_dir):
    validate(config)
    state_dir = Path(state_dir).resolve()
    if state_dir.stat().st_uid != os.geteuid() or state_dir.stat().st_mode & 0o077:
        raise BackupError('Deployment state must be owned by the operator and mode 0700')
    with (state_dir / 'deploy.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        status_path = state_dir / 'backup-status.json'
        status = deploy.read_json(status_path) if status_path.exists() else {}
        status.update(attemptAt=now(), outcome='running')
        deploy.write_json(status_path, status)
        try:
            manifest = create_locked(config, state_dir)
            status.update(outcome='success', finishedAt=now(), lastSuccess=manifest)
            deploy.write_json(status_path, status)
            print(json.dumps({'event': 'backup_success', **manifest}), flush=True)
            return manifest
        except Exception:
            status.update(outcome='failed', finishedAt=now())
            deploy.write_json(status_path, status)
            print(json.dumps({'event': 'backup_failed', 'at': now()}), flush=True)
            raise


def create_locked(config, state_dir):
    current = deploy.read_json(state_dir / 'current.json')
    attempt = deploy.read_json(state_dir / 'attempt.json')
    model = current['compose']
    # current.json contains Compose-escaped values. Restore literal strings without shell evaluation.
    env = {k: v.replace('$$', '$') for k, v in model['services']['backend']['environment'].items()}
    database, owner, assets_bucket = env['DB_NAME'], env['DB_USERNAME'], env['ASSETS_BUCKET']
    preflight(config, assets_bucket)
    compose_file = state_dir / 'backup.compose.json'
    deploy.write_json(compose_file, model)
    try:
        cid = deploy.compose({'name': current['name']}, compose_file, 'ps', '-q', 'postgres').strip()
        info = json.loads(deploy.run('docker', 'inspect', cid))[0]
        if info['State']['Health']['Status'] != 'healthy':
            raise BackupError('Database is not healthy')
        init = deploy.read_json(state_dir / 'initialization.json')
        volume = model['volumes']['data']['name']
        volume_info = json.loads(deploy.run('docker', 'volume', 'inspect', volume))[0]
        labels = volume_info.get('Labels') or {}
        if (init['state_dir'] != str(state_dir) or init['name'] != current['name']
                or labels.get('abservice.cohost.state') != init['id']
                or labels.get('abservice.cohost') != current['name']
                or not any(m.get('Name') == volume and m['Destination'] == '/var/lib/postgresql/data' for m in info['Mounts'])):
            raise BackupError('Database volume does not belong to this deployment state')
        snapshot_at = now()  # Conservative bound before pg_dump opens its consistent snapshot.
        run_id = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ-') + uuid.uuid4().hex
        prefix = config['prefix'] + '/runs/' + run_id
        with tempfile.TemporaryDirectory(prefix='backup-', dir=state_dir) as directory:
            root = Path(directory)
            dump = root / 'db.dump'
            dump_database(cid, database, dump)
            assets = root / 'assets.json'
            deploy.write_json(assets, inventory(config, assets_bucket))
            files = {p.name: put(config, prefix + '/' + p.name, p) for p in (dump, assets)}
            manifest = {'format': 1, 'snapshotAt': snapshot_at, 'completedAt': now(), 'source': current['source'],
                        'image': current['image'], 'postgresImage': model['services']['postgres']['image'],
                        'database': {'name': database, 'owner': owner, 'migrationHistoryTable': 'public.flyway_schema_history'},
                        'files': files,
                        # A failed application update must not stop preservation of a readable DB.
                        'deploymentAttempt': {k: attempt[k] for k in ('source', 'image', 'status', 'time')}}
            path = root / 'manifest.json'
            deploy.write_json(path, manifest)
            # Completion marker is always last. Failed attempts never publish a complete restore point.
            stored = put(config, prefix + '/manifest.json', path)
            return {'snapshotAt': snapshot_at, 'completedAt': manifest['completedAt'], 'bucket': config['bucket'], **stored}
    finally:
        compose_file.unlink(missing_ok=True)


def fetch(config, manifest_key, manifest_version, destination, include_assets=False):
    validate(config)
    if not manifest_key.startswith(config['prefix'] + '/runs/') or not manifest_key.endswith('/manifest.json'):
        raise BackupError('Manifest key is outside the configured prefix')
    destination = Path(destination)
    destination.mkdir(mode=0o700, parents=False, exist_ok=False)
    path = destination / 'manifest.json'
    if not manifest_version or manifest_version == 'null':
        raise BackupError('Choose an explicit manifest version')
    aws(config, 'get-object', '--bucket', config['bucket'], '--key', manifest_key, '--version-id', manifest_version,
        '--checksum-mode', 'ENABLED', str(path))
    manifest = deploy.read_json(path)
    if manifest.get('format') != 1 or set(manifest['files']) != {'db.dump', 'assets.json'}:
        raise BackupError('Unsupported backup format')
    prefix = manifest_key.rsplit('/', 1)[0]
    for name, artifact in manifest['files'].items():
        if artifact['key'] != prefix + '/' + name or not artifact['versionId']:
            raise BackupError('Artifact does not belong to the selected restore point')
        output = destination / name
        aws(config, 'get-object', '--bucket', config['bucket'], '--key', artifact['key'],
            '--version-id', artifact['versionId'], '--checksum-mode', 'ENABLED', str(output))
        if output.stat().st_size != artifact['bytes'] or checksum(output) != artifact['sha256']:
            raise BackupError('Downloaded artifact checksum mismatch')
    if include_assets:
        assets = deploy.read_json(destination / 'assets.json')
        asset_dir = destination / 'assets'
        asset_dir.mkdir(mode=0o700)
        for obj in assets['versions']:
            if not obj['Key'].startswith('assets/') or not obj['VersionId']:
                raise BackupError('Invalid asset reference')
            # Never turn an S3 key into a local path. Retain the key/version mapping in assets.json.
            name = hashlib.sha256((obj['Key'] + '\0' + obj['VersionId']).encode()).hexdigest()
            output = asset_dir / name
            response = aws(config, 'get-object', '--bucket', assets['bucket'], '--key', obj['Key'],
                           '--version-id', obj['VersionId'], '--checksum-mode', 'ENABLED', str(output))
            if output.stat().st_size != obj['Size'] or response['ETag'] != obj['ETag']:
                raise BackupError('Asset version does not match the saved inventory')
    print(json.dumps({'event': 'backup_fetched', 'snapshotAt': manifest['snapshotAt']}), flush=True)
    return manifest


def freshness(config, max_age_hours):
    """Read from S3, so this can run with an operator login after losing the host."""
    validate(config)
    if not 0 < max_age_hours < 8760:
        raise BackupError('Invalid age threshold')
    objects = aws(config, 'list-objects-v2', '--bucket', config['bucket'], '--prefix', config['prefix'] + '/runs/').get('Contents', [])
    candidates = [obj for obj in objects if obj['Key'].endswith('/manifest.json')]
    if not candidates:
        raise BackupError('No complete restore point')
    latest = max(candidates, key=lambda obj: obj['LastModified'])
    with tempfile.TemporaryDirectory(prefix='backup-freshness-') as directory:
        path = Path(directory) / 'manifest.json'
        response = aws(config, 'get-object', '--bucket', config['bucket'], '--key', latest['Key'], '--checksum-mode', 'ENABLED', str(path))
        manifest = deploy.read_json(path)
    if manifest.get('format') != 1 or set(manifest['files']) != {'db.dump', 'assets.json'}:
        raise BackupError('Invalid completion marker')
    snapshot = dt.datetime.fromisoformat(manifest['snapshotAt'])
    age = (dt.datetime.now(dt.timezone.utc) - snapshot).total_seconds() / 3600
    if age < 0:
        raise BackupError('Snapshot timestamp is in the future')
    result = {'event': 'backup_freshness', 'snapshotAt': manifest['snapshotAt'], 'ageHours': age,
              'fresh': age <= max_age_hours, 'key': latest['Key'], 'versionId': response['VersionId']}
    print(json.dumps(result), flush=True)
    if not result['fresh']:
        raise BackupError('Restore point is stale')
    return result


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', required=True)
    sub = parser.add_subparsers(dest='command', required=True)
    create_parser = sub.add_parser('create')
    create_parser.add_argument('--state-dir', required=True)
    fetch_parser = sub.add_parser('fetch')
    fetch_parser.add_argument('--manifest-key', required=True)
    fetch_parser.add_argument('--manifest-version', required=True)
    fetch_parser.add_argument('--destination', required=True)
    fetch_parser.add_argument('--assets', action='store_true')
    fresh_parser = sub.add_parser('freshness')
    fresh_parser.add_argument('--max-age-hours', type=float, required=True)
    args = parser.parse_args()
    try:
        config = deploy.read_json(Path(args.config))
        if args.command == 'create':
            create(config, args.state_dir)
        elif args.command == 'fetch':
            fetch(config, args.manifest_key, args.manifest_version, args.destination, args.assets)
        else:
            freshness(config, args.max_age_hours)
    except Exception:
        print('Backup operation failed; inspect protected host state. Sensitive output suppressed.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
