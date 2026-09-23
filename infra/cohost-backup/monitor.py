"""Check completed S3 backups independently of the database host."""
import datetime as dt
import json
import os
import re


def check(s3, bucket, prefix, max_age_hours, now):
    latest = None
    for page in s3.get_paginator('list_objects_v2').paginate(Bucket=bucket, Prefix=prefix + '/runs/'):
        for obj in page.get('Contents', []):
            if obj['Key'].endswith('/manifest.json') and (latest is None or obj['LastModified'] > latest['LastModified']):
                latest = obj
    if latest is None:
        return {'healthy': False, 'reason': 'missing'}
    response = s3.get_object(Bucket=bucket, Key=latest['Key'])
    with response['Body'] as stream:
        raw = stream.read(65537)
    if len(raw) > 65536 or not response.get('VersionId') or response['VersionId'] == 'null':
        raise ValueError('Invalid completion marker')
    manifest = json.loads(raw)
    if manifest.get('format') != 1 or set(manifest['files']) != {'db.dump', 'assets.json'}:
        raise ValueError('Invalid completion marker')
    snapshot = dt.datetime.fromisoformat(manifest['snapshotAt'])
    completed = dt.datetime.fromisoformat(manifest['completedAt'])
    if snapshot.tzinfo is None or completed.tzinfo is None or not snapshot <= completed <= now:
        raise ValueError('Invalid snapshot time')
    directory = latest['Key'].rsplit('/', 1)[0]
    for name, artifact in manifest['files'].items():
        if (artifact['key'] != directory + '/' + name or not artifact['versionId']
                or artifact['versionId'] == 'null' or type(artifact['bytes']) is not int
                or artifact['bytes'] <= 0 or not re.fullmatch('[0-9a-f]{64}', artifact['sha256'])):
            raise ValueError('Invalid artifact reference')
        # Read only metadata, not the database dump. Restore tests check the actual checksum.
        head = s3.head_object(Bucket=bucket, Key=artifact['key'], VersionId=artifact['versionId'])
        if head['ContentLength'] != artifact['bytes']:
            raise ValueError('Missing or truncated artifact')
    age = (now - snapshot).total_seconds() / 3600
    return {'healthy': age <= max_age_hours, 'reason': 'fresh' if age <= max_age_hours else 'stale',
            'snapshotAt': manifest['snapshotAt'], 'ageHours': round(age, 3)}


def publish(s3, cloudwatch, config, now):
    try:
        result = check(s3, config['bucket'], config['prefix'], config['max_age_hours'], now)
    except Exception:
        # Never log S3 contents, raw SDK responses, environment values or exception text.
        result = {'healthy': False, 'reason': 'unreadable'}
    try:
        cloudwatch.put_metric_data(Namespace='ABService/Backup', MetricData=[{
            'MetricName': 'Unhealthy', 'Dimensions': [{'Name': 'Backup', 'Value': config['name']}],
            'Timestamp': now, 'Value': 0 if result['healthy'] else 1, 'Unit': 'Count'}])
    except Exception:
        # No successful metric means missing data; the external alarm treats it as a failure.
        raise RuntimeError('Backup health metric could not be published') from None
    print(json.dumps({'event': 'backup_check', **result}), flush=True)
    return result


def handler(event, context):
    import boto3
    from botocore.config import Config
    client_config = Config(connect_timeout=3, read_timeout=5, retries={'total_max_attempts': 2})
    return publish(boto3.client('s3', config=client_config), boto3.client('cloudwatch', config=client_config), {
        'bucket': os.environ['BACKUP_BUCKET'], 'prefix': os.environ['BACKUP_PREFIX'],
        'name': os.environ['BACKUP_NAME'], 'max_age_hours': float(os.environ['MAX_AGE_HOURS'])
    }, dt.datetime.now(dt.timezone.utc))
