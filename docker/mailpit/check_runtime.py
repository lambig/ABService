"""Check the built Mailpit image's privileges and SMTP-to-HTTP delivery."""
import argparse
from email.message import EmailMessage
import json
import smtplib
import subprocess
import time
import urllib.error
import urllib.request
import uuid


def docker(*args):
    return subprocess.check_output(['docker', *args], text=True, timeout=30).strip()


def wait_for_json(url, predicate):
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=3) as response:
                value = json.load(response)
            if predicate(value):
                return value
        except (OSError, urllib.error.URLError):
            pass
        time.sleep(0.5)
    raise RuntimeError('Mailpit did not return the expected API response')


def check(image):
    config_user = docker('image', 'inspect', image, '--format', '{{.Config.User}}')
    assert config_user == '65534:65534', f'Unexpected image user: {config_user!r}'
    name = 'mailpit-check-' + uuid.uuid4().hex
    try:
        docker('run', '--detach', '--name', name,
               '--publish', '127.0.0.1::1025', '--publish', '127.0.0.1::8025', image)
        status = docker('exec', name, 'cat', '/proc/1/status')
        ids = {line.split(':', 1)[0]: line.split()[1:]
               for line in status.splitlines() if line.startswith(('Uid:', 'Gid:'))}
        assert ids['Uid'] == ['65534'] * 4, ids
        assert ids['Gid'] == ['65534'] * 4, ids
        http_port = int(docker('port', name, '8025/tcp').rsplit(':', 1)[1])
        smtp_port = int(docker('port', name, '1025/tcp').rsplit(':', 1)[1])
        endpoint = f'http://127.0.0.1:{http_port}/api/v1/messages'
        wait_for_json(endpoint, lambda value: 'messages' in value)
        message = EmailMessage()
        message['From'] = 'sender@example.test'
        message['To'] = 'recipient@example.test'
        message['Subject'] = name
        message.set_content('Disposable SMTP regression fixture.')
        with smtplib.SMTP('127.0.0.1', smtp_port, timeout=10) as smtp:
            smtp.send_message(message)
        wait_for_json(endpoint, lambda value: any(
            item.get('Subject') == name for item in value.get('messages', [])))
        print(json.dumps({'config_user': config_user, 'process_ids': ids,
                          'smtp_to_http': 'passed'}))
    except Exception:
        subprocess.run(['docker', 'logs', name], timeout=15, check=False)
        raise
    finally:
        subprocess.run(['docker', 'rm', '--force', name], timeout=30, check=False)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--image', required=True)
    check(parser.parse_args().image)
