"""Optional Linux/amd64 test-image cache; never cache runtime data or credentials."""

import json
from pathlib import Path
import re
import subprocess
import tempfile


LOCK = Path(__file__).with_name('image-cache-lock.json')


def registry_throttled(stderr):
    if isinstance(stderr, bytes):
        stderr = stderr.decode('utf-8', errors='replace')
    return 'toomanyrequests:' in (stderr or '').lower()


def cached_image(image, directory, run, pull):
    lock = json.loads(LOCK.read_text())
    if image != lock['image'] or lock['platform'] != 'linux/amd64':
        raise ValueError('Image cache lock does not match the Compose image/platform')
    image_id = lock['config']
    if any(not re.fullmatch(r'sha256:[0-9a-f]{64}', lock[key]) for key in ('config', 'manifest')):
        raise ValueError('Invalid pinned image digest')
    if run('docker', 'info', '--format', '{{.OSType}}/{{.Architecture}}') not in (
            'linux/x86_64', 'linux/amd64'):
        raise ValueError('The image cache requires a Linux/amd64 Docker daemon')
    directory = Path(directory).absolute()
    # Use a trusted parent: another process must not replace path components.
    if any(path.is_symlink() for path in (directory, *directory.parents)):
        raise ValueError('Image cache directory must not contain symlinks')
    try:
        directory.mkdir(parents=True)
    except FileExistsError:
        if not directory.is_dir():
            raise ValueError('Image cache path must be a directory')
    else:
        # Only a newly created directory is made readable by actions/cache.
        # Existing directories may hold private files; preserve their mode.
        directory.chmod(0o755)
    archive = directory / (image_id.removeprefix('sha256:') + '.tar')
    if archive.is_symlink():
        raise ValueError('Image cache archive must not be a symlink')
    hit = archive.is_file()
    if hit:
        run('docker', 'image', 'load', '--input', str(archive), timeout=120)
        reference = image_id
    else:
        reference = image
        try:
            pull(image)
        except subprocess.CalledProcessError as error:
            if not registry_throttled(error.stderr):
                raise
            # AWS publishes the identical amd64 manifest on Docker Hub. Only
            # exhausted registry throttling permits this bounded fallback.
            reference = 'docker.io/amazon/cloudwatch-agent@' + lock['manifest']
            print('Public ECR throttled; trying the identical pinned AWS Docker Hub image', flush=True)
            pull(reference)
    actual = json.loads(run('docker', 'image', 'inspect', reference))[0]
    if (actual['Id'], actual['Os'], actual['Architecture']) != (image_id, 'linux', 'amd64'):
        raise ValueError('Cached/pulled image does not match the pinned config and platform')
    if not hit:
        # Save only the image, before any test containers or credentials are used.
        # Reserve a fresh file instead of following a pre-existing .partial link.
        with tempfile.NamedTemporaryFile(dir=directory, suffix='.partial', delete=False) as output:
            temporary = Path(output.name)
        try:
            run('docker', 'image', 'save', '--output', str(temporary), image_id, timeout=120)
            temporary.chmod(0o644)
            temporary.replace(archive)
        finally:
            temporary.unlink(missing_ok=True)
    print(f'Transport image cache {"hit" if hit else "miss"}: verified {image_id}', flush=True)
    # Docker archives need not retain RepoDigests. Use the verified content ID,
    # not a restored mutable tag, and keep Compose startup at --pull never.
    return image_id
