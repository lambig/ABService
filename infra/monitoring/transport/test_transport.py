"""Explicit root-only integration test on a disposable systemd/Docker Linux host.

Uses invalid credentials and an internal Docker network with a local fake Logs API.
No AWS resources are created. Run this file directly, not through unittest discovery.
"""

import collections
import datetime as dt
import grp
import gzip
import http.server
import json
import os
from pathlib import Path
import pwd
import shutil
import subprocess
import tempfile
import threading
import time


SOURCE = Path(__file__).resolve().parent


def run(*args, **kwargs):
    return subprocess.run(args, check=True, capture_output=True, text=True, timeout=300, **kwargs).stdout.strip()


def wait(check, description, seconds=90):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if check():
            return
        time.sleep(0.2)
    raise AssertionError(description)


class Receiver(http.server.BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass  # Never log authorization headers or request bodies.

    def do_POST(self):
        payload = self.rfile.read(int(self.headers['Content-Length']))
        if self.headers.get('Content-Encoding') == 'gzip':
            payload = gzip.decompress(payload)
        body = json.loads(payload)
        operation = self.headers.get('X-Amz-Target', '').split('.')[-1]
        failed = operation == 'PutLogEvents' and self.server.outage.is_set()
        with self.server.lock:
            if operation == 'PutLogEvents':
                target = self.server.rejected if failed else self.server.accepted
                target.extend(json.loads(event['message']) for event in body['logEvents'])
                if not failed:
                    self.server.envelopes.extend({**event, 'group': body['logGroupName']}
                                                 for event in body['logEvents'])
        self.send_response(503 if failed else 200)
        self.send_header('Content-Type', 'application/x-amz-json-1.1')
        self.end_headers()
        self.wfile.write(json.dumps(
            {'__type': 'ServiceUnavailableException', 'message': 'synthetic outage'} if failed
            else {'nextSequenceToken': '1', 'logStreams': []}).encode())


def main():
    if os.geteuid() != 0:
        raise SystemExit('Run explicitly with sudo on a disposable systemd/Docker host')
    account = pwd.getpwnam('nobody')
    group = grp.getgrgid(account.pw_gid).gr_name
    directory = Path(tempfile.mkdtemp(prefix='ab-transport-test-', dir='/run'))
    name = directory.name
    runtime = Path('/run') / (name + '-credentials')
    logs = Path('/var/log') / name
    unit = Path('/run/systemd/system') / (name + '.service')
    rotate_unit = unit.with_name(name + '-rotate.service')
    rotate_state = Path('/var/lib') / name
    receiver = None
    network_created = False
    compose = []

    def owned(path, mode=0o700):
        path.mkdir(mode=mode)
        os.chown(path, account.pw_uid, account.pw_gid)
        return path

    try:
        directory.chmod(0o755)
        config_dir = owned(directory / 'config')
        state = owned(directory / 'state')
        auth = owned(directory / 'auth')
        for filename in ('certificate.pem', 'private-key.pem'):
            path = auth / filename
            path.write_text('deliberately invalid test material')
            path.chmod(0o600)
            os.chown(path, account.pw_uid, account.pw_gid)
        shutil.copyfile(SOURCE.parent / 'credentials/refresh.py', directory / 'refresh.py')
        helper = directory / 'helper'
        helper.write_text(
            '#!/usr/bin/python3\nimport datetime, json, pathlib, sys\n'
            "if pathlib.Path(__file__).with_name('fail').exists(): sys.exit(1)\n"
            'now = datetime.datetime.now(datetime.timezone.utc)\n'
            "print(json.dumps({'Version': 1, 'AccessKeyId': 'INVALID_TRANSPORT_TEST',\n"
            " 'SecretAccessKey': 'INVALID_TEST_SECRET', 'SessionToken': 'INVALID_TEST_TOKEN',\n"
            " 'Expiration': (now + datetime.timedelta(seconds=900)).isoformat()}))\n")
        helper.chmod(0o755)
        publisher_config = directory / 'publisher.json'
        publisher_config.write_text(json.dumps({
            'helper': str(helper), 'certificate_directory': str(auth),
            'trust_anchor_arn': 'invalid-test-anchor', 'profile_arn': 'invalid-test-profile',
            'role_arn': 'invalid-test-role'}))
        service = (SOURCE.parent / 'credentials/abservice-monitor-credentials.service').read_text()
        for old, new in {
            'User=abservice-monitor': 'User=nobody',
            'Group=abservice-monitor': f'Group={group}',
            'RuntimeDirectory=abservice-monitor': f'RuntimeDirectory={runtime.name}',
            '/opt/abservice/monitor-credentials/refresh.py': str(directory / 'refresh.py'),
            '/etc/abservice/monitor-credentials.json': str(publisher_config),
            '/run/abservice-monitor': str(runtime),
        }.items():
            assert service.count(old) == 1
            service = service.replace(old, new)
        dropin = (SOURCE / 'publisher-file.conf').read_text().replace(
            'LogsDirectory=abservice-monitor', f'LogsDirectory={name}').replace(
            '/var/log/abservice-monitor', str(logs))
        unit.write_text(service + '\n' + dropin)
        tmpfiles = directory / 'tmpfiles.conf'
        tmpfiles.write_text((SOURCE / 'tmpfiles.conf').read_text().replace(
            '/var/log/abservice-monitor', str(logs)).replace('abservice-monitor abservice-monitor', f'nobody {group}'))
        run('systemd-tmpfiles', '--create', str(tmpfiles))
        run('systemd-analyze', 'verify', str(unit),
            str(SOURCE / 'abservice-monitor-logrotate.service'),
            str(SOURCE / 'abservice-monitor-logrotate.timer'))
        run('systemctl', 'daemon-reload')
        log = logs / 'credentials.jsonl'

        def publish(failure=False):
            # Success timestamps are second precision; each synthetic event is distinguishable.
            time.sleep(1.1)
            marker = directory / 'fail'
            if failure:
                marker.touch()
            result = subprocess.run(['systemctl', 'start', unit.name], capture_output=True, timeout=75)
            marker.unlink(missing_ok=True)
            assert (result.returncode != 0) == failure, result.stderr.decode() + run(
                'journalctl', '-u', unit.name, '--no-pager', '-n', '15')
            event = json.loads(log.read_text().splitlines()[-1])
            assert event['event'] == ('credential_refresh_failure' if failure else 'credential_refresh_success')
            assert 'INVALID_' not in log.read_text()
            return event

        first = publish()
        assert logs.stat().st_mode & 0o777 == 0o700
        assert log.stat().st_mode & 0o777 == 0o600
        assert log.stat().st_uid == account.pw_uid
        rotation = directory / 'rotation.conf'
        rotation.write_text((SOURCE / 'credentials.logrotate').read_text().replace(
            '/var/log/abservice-monitor', str(logs)).replace('abservice-monitor abservice-monitor', f'nobody {group}'))
        rotation_service = (SOURCE / 'abservice-monitor-logrotate.service').read_text()
        for old, new in {
            'abservice-monitor-credentials.service': unit.name,
            '/var/log/abservice-monitor': str(logs),
            'User=abservice-monitor': 'User=nobody',
            'Group=abservice-monitor': f'Group={group}',
            'StateDirectory=abservice-monitor-logrotate': f'StateDirectory={name}',
            '/var/lib/abservice-monitor-logrotate': str(rotate_state),
            '/etc/abservice/monitor-logrotate.conf': str(rotation),
            '/usr/sbin/logrotate --state': '/usr/sbin/logrotate --force --state',
        }.items():
            rotation_service = rotation_service.replace(old, new)
        rotate_unit.write_text(rotation_service)
        run('systemctl', 'daemon-reload')

        def rotate():
            inode = log.stat().st_ino
            contents = log.read_bytes()
            run('systemctl', 'start', rotate_unit.name)
            assert log.stat().st_ino != inode
            assert log.with_suffix('.jsonl.1').read_bytes() == contents
            assert log.stat().st_mode & 0o777 == 0o600

        run('docker', 'network', 'create', '--internal', name)
        network_created = True
        network = json.loads(run('docker', 'network', 'inspect', name))[0]
        assert network['Internal']
        gateway = network['IPAM']['Config'][0]['Gateway']
        receiver = http.server.ThreadingHTTPServer((gateway, 0), Receiver)
        receiver.outage = threading.Event()
        receiver.lock = threading.Lock()
        receiver.accepted, receiver.rejected, receiver.envelopes = [], [], []
        threading.Thread(target=receiver.serve_forever, daemon=True).start()
        config = json.loads((SOURCE / 'agent.example.json').read_text())
        config['agent']['region'] = 'us-east-1'
        config['logs']['endpoint_override'] = f'http://{gateway}:{receiver.server_port}'
        config['logs']['force_flush_interval'] = 1
        collect = config['logs']['logs_collected']['files']['collect_list'][0]
        collect.update(log_group_name='invalid-transport-test', log_stream_name=name)
        replay_config = json.loads((SOURCE / 'replay.agent.example.json').read_text())
        replay_collect = replay_config['logs']['logs_collected']['files']['collect_list'][0]
        replay_collect.update(log_group_name='invalid-replay-test', log_stream_name=name)
        config['logs']['logs_collected']['files']['collect_list'].append(replay_collect)
        (config_dir / 'agent.json').write_text(json.dumps(config))
        shutil.copyfile(SOURCE / 'common-config.toml', config_dir / 'common-config.toml')
        override = directory / 'compose.json'
        override.write_text(json.dumps({
            'services': {s: {'networks': ['lab']} for s in ('translate', 'agent')},
            'networks': {'lab': {'external': True, 'name': name}}}))
        env = dict(os.environ, MONITOR_UID=str(account.pw_uid), MONITOR_GID=str(account.pw_gid),
                   MONITOR_CONFIG_DIR=str(config_dir), MONITOR_CREDENTIALS_DIR=str(runtime),
                   MONITOR_LOG_DIR=str(logs), MONITOR_STATE_DIR=str(state))
        compose = ['docker', 'compose', '-p', name, '-f', str(SOURCE / 'compose.yml'), '-f', str(override)]

        def dc(*args):
            return run(*compose, *args, env=env)

        def received(events):
            with receiver.lock:
                return all(event in receiver.accepted for event in events)

        def verify_timestamps(events, expected_group='invalid-transport-test'):
            with receiver.lock:
                delivered = list(receiver.envelopes)
            for event in events:
                matches = [item for item in delivered if json.loads(item['message']) == event]
                assert matches, event
                assert all(item['group'] == expected_group for item in matches)
                # Both publisher payload and CloudWatch envelope retain the original
                # successful execution's whole second, never its expiry or read time.
                assert all(item['timestamp'] == event['last_success_epoch'] * 1000 for item in matches), matches

        def synthetic_success(seconds_ago, fractional=True):
            when = dt.datetime.now(dt.timezone.utc) - dt.timedelta(seconds=seconds_ago)
            when = when.replace(microsecond=123456 if fractional else 0)
            # Put expiry first to ensure the format selects the success key, not
            # the first ISO timestamp. Exercise both fractional and whole seconds.
            return {'version': 1, 'event': 'credential_refresh_success',
                    'credential_expires_at': (when + dt.timedelta(seconds=900)).isoformat(),
                    'last_success_at': when.isoformat(), 'last_success_epoch': int(when.timestamp())}

        def append_event(path, event):
            with path.open('a') as output:
                output.write(json.dumps(event) + '\n')

        # Old archive has a newer mtime than the active file. Normal collection
        # must not choose it when the agent starts with empty state.
        archive_only = synthetic_success(600)
        archive = log.with_suffix('.jsonl.1')
        append_event(archive, archive_only)
        archive.chmod(0o600)
        os.chown(archive, account.pw_uid, account.pw_gid)
        os.utime(archive, (time.time() + 10, time.time() + 10))

        dc('up', '-d')
        wait(lambda: received([first]), 'initial event was not delivered')
        verify_timestamps([first])
        assert not received([archive_only]), 'archive entered normal collection'

        delayed = [synthetic_success(300), synthetic_success(240, fractional=False)]
        for event in delayed:
            append_event(log, event)
        wait(lambda: received(delayed), 'delayed success events were not delivered')
        verify_timestamps(delayed)
        # Replay uses a separate file and destination without the freshness metric.
        replay_log = logs / 'replay.jsonl'
        replayed = synthetic_success(540)
        append_event(replay_log, replayed)
        replay_log.chmod(0o600)
        os.chown(replay_log, account.pw_uid, account.pw_gid)
        wait(lambda: received([replayed]), 'separate replay was not delivered')
        verify_timestamps([replayed], 'invalid-replay-test')
        print('PASS original success timestamps, expiry-first JSON, fractional/whole seconds, isolated replay', flush=True)
        container = json.loads(run('docker', 'inspect', dc('ps', '-q', 'agent')))[0]
        mounts = {m['Destination']: m for m in container['Mounts']}
        assert all(not mounts[path]['RW'] for path in (
            '/run/abservice-monitor', '/var/log/abservice-monitor', '/etc/abservice-monitor'))
        assert mounts['/opt/aws/amazon-cloudwatch-agent/logs']['RW']
        assert container['HostConfig']['ReadonlyRootfs']
        assert set(container['NetworkSettings']['Networks']) == {name}
        rotate()
        normal = publish()
        wait(lambda: received([normal]), 'running-agent rotation lost event')
        verify_timestamps([normal])
        print('PASS initial shipping, restrictive file modes/mounts, rename rotation', flush=True)

        receiver.outage.set()
        queued = publish()
        wait(lambda: queued in receiver.rejected, 'outage was not exercised')
        receiver.outage.clear()
        wait(lambda: received([queued]), 'unrotated retry lost event', seconds=120)
        print('PASS destination outage and recovery without rotation', flush=True)

        receiver.outage.set()
        interrupted = publish()
        wait(lambda: interrupted in receiver.rejected, 'rotation outage was not exercised')
        rotate()
        queued_after = publish()
        failed = publish(failure=True)
        # The sender may block later batches behind the first retry; keep the outage
        # active while the agent can discover the new source, then restore the API.
        time.sleep(3)
        receiver.outage.clear()
        wait(lambda: received([queued_after, failed]), 'new-file shipping did not recover', seconds=120)

        def observe_retained(events, description):
            with receiver.lock:
                missing = [event for event in events if event not in receiver.accepted]
            retained = [json.loads(line) for path in logs.glob('credentials.jsonl*')
                        for line in path.read_text().splitlines()]
            assert all(event in retained for event in missing)
            print(f'OBSERVED {description}: {len(missing)}/{len(events)} events not delivered; '
                  'all missing events locally retained', flush=True)

        observe_retained([interrupted], 'outage across rotation with agent alive')

        dc('stop', '-t', '15', 'agent')
        restart_events = [publish(), publish()]
        dc('start', 'agent')
        wait(lambda: received(restart_events), 'persisted-state restart lost new events')
        print('PASS restart with persisted state and an unrotated source', flush=True)

        dc('stop', '-t', '15', 'agent')
        offline = [publish()]
        rotate()
        offline.append(publish())
        rotate()
        newest = publish()
        dc('start', 'agent')
        wait(lambda: received([newest]), 'restart failed to discover newest file')
        time.sleep(3)
        observe_retained(offline, 'stopped across rotations')
        assert not received([archive_only]), 'archive leaked into live collection'
        with receiver.lock:
            successes = [event for event in receiver.accepted
                         if event['event'] == 'credential_refresh_success' and event != replayed]
        verify_timestamps(successes)
        with receiver.lock:
            counts = collections.Counter(json.dumps(e, sort_keys=True) for e in receiver.accepted)
        print(f'OBSERVED duplicate deliveries={sum(n - 1 for n in counts.values())}', flush=True)
        dc('stop', '-t', '15', 'agent')
        for _ in range(9):
            publish()
            rotate()
        assert len(list(logs.glob('credentials.jsonl.*'))) == 7
        assert all(path.stat().st_mode & 0o777 == 0o600 for path in logs.glob('credentials.jsonl*'))
        print('PASS archive count bounded to seven; no exactly-once/durable-queue claim', flush=True)
    except Exception:
        if compose:
            result = subprocess.run([*compose, 'logs', '--tail', '60'], env=env, capture_output=True, text=True)
            print(result.stdout.replace('INVALID_TEST_SECRET', '[invalid fixture]'))
        raise
    finally:
        cleanup_errors = []
        if compose:
            result = subprocess.run([*compose, 'down', '--timeout', '15'], env=env, capture_output=True)
            if result.returncode:
                cleanup_errors.append(result.stderr.decode())
        if receiver:
            receiver.shutdown()
            receiver.server_close()
        if network_created:
            result = subprocess.run(['docker', 'network', 'rm', name], capture_output=True)
            if result.returncode:
                cleanup_errors.append(result.stderr.decode())
        subprocess.run(['systemctl', 'stop', unit.name, rotate_unit.name], capture_output=True)
        subprocess.run(['systemctl', 'reset-failed', unit.name, rotate_unit.name], capture_output=True)
        unit.unlink(missing_ok=True)
        rotate_unit.unlink(missing_ok=True)
        run('systemctl', 'daemon-reload')
        for path in (runtime, logs, directory, rotate_state):
            assert path.parent in (Path('/run'), Path('/var/log'), Path('/var/lib'))
            assert path.name.startswith('ab-transport-test-') and not path.is_symlink()
            if path.exists():
                shutil.rmtree(path)
        assert not any(path.exists() for path in (unit, rotate_unit, runtime, logs, directory, rotate_state))
        assert not cleanup_errors, cleanup_errors
        print('CLEANUP temporary containers, network, unit and directories removed', flush=True)


if __name__ == '__main__':
    main()
