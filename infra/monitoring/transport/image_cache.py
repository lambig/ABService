"""Optional Linux/amd64 test-image cache; never cache runtime data or credentials."""

import hashlib
import json
from pathlib import Path
import re
import subprocess
import tarfile
import tempfile


LOCK = Path(__file__).with_name('image-cache-lock.json')


def registry_throttled(stderr):
    if isinstance(stderr, bytes):
        stderr = stderr.decode('utf-8', errors='replace')
    return 'toomanyrequests:' in (stderr or '').lower()


def loaded_reference(output):
    references = {line.split(': ', 1)[1] for line in output.splitlines()
                  if line.startswith(('Loaded image ID: ', 'Loaded image: '))}
    if len(references) != 1:
        raise ValueError('Image load must identify exactly one restored image')
    return references.pop()


def verify_archive(archive, config_digest):
    # Read the daemon's export without extracting paths into the filesystem.
    # Image IDs name configs in the classic store, but manifests/indexes in containerd.
    with tarfile.open(archive, 'r:') as saved:
        def read(name):
            member = saved.getmember(name)
            if not member.isfile() or member.size > 1024 * 1024:
                raise ValueError('Invalid image metadata in Docker archive')
            return saved.extractfile(member).read()

        manifest = json.loads(read('manifest.json'))
        if not isinstance(manifest, list) or len(manifest) != 1:
            raise ValueError('Image archive must contain exactly one platform image')
        config = read(manifest[0]['Config'])
        if 'sha256:' + hashlib.sha256(config).hexdigest() != config_digest:
            raise ValueError('Image config does not match the pinned content')
        metadata = json.loads(config)
        if (metadata.get('os'), metadata.get('architecture')) != ('linux', 'amd64'):
            raise ValueError('Image config does not match the pinned platform')


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
        reference = loaded_reference(run('docker', 'image', 'load', '--input', str(archive), timeout=120))
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
    runtime_id = actual['Id']
    if not re.fullmatch(r'sha256:[0-9a-f]{64}', runtime_id):
        raise ValueError('Docker did not return an immutable image ID')
    if (actual['Os'], actual['Architecture']) != ('linux', 'amd64'):
        raise ValueError('Cached/pulled image does not match the pinned platform')
    # Verify the exported config bytes (including layer diff IDs), not the store's ID convention.
    # Docker validates layer content on load/pull. Always export by immutable ID, never by a tag.
    with tempfile.NamedTemporaryFile(dir=directory, suffix='.partial', delete=False) as output:
        temporary = Path(output.name)
    try:
        run('docker', 'image', 'save', '--output', str(temporary), runtime_id, timeout=120)
        verify_archive(temporary, image_id)
        if not hit:
            temporary.chmod(0o644)
            temporary.replace(archive)
    finally:
        temporary.unlink(missing_ok=True)
    print(f'Transport image cache {"hit" if hit else "miss"}: verified config {image_id}, image {runtime_id}',
          flush=True)
    # Use the verified daemon ID, not a config digest or a restored mutable tag.
    return runtime_id
