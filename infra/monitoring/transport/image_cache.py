"""Optional Linux/amd64 test-image cache; never cache runtime data or credentials."""

import json
from pathlib import Path
import re


LOCK = Path(__file__).with_name('image-cache-lock.json')


def cached_image(image, directory, run, pull):
    lock = json.loads(LOCK.read_text())
    if image != lock['image'] or lock['platform'] != 'linux/amd64':
        raise ValueError('Image cache lock does not match the Compose image/platform')
    image_id = lock['config']
    if not re.fullmatch(r'sha256:[0-9a-f]{64}', image_id):
        raise ValueError('Invalid pinned image config digest')
    if run('docker', 'info', '--format', '{{.OSType}}/{{.Architecture}}') not in (
            'linux/x86_64', 'linux/amd64'):
        raise ValueError('The image cache requires a Linux/amd64 Docker daemon')
    directory = Path(directory)
    directory.mkdir(parents=True, exist_ok=True)
    archive = directory / (image_id.removeprefix('sha256:') + '.tar')
    hit = archive.is_file()
    if hit:
        run('docker', 'image', 'load', '--input', str(archive), timeout=120)
        reference = image_id
    else:
        pull(image)
        reference = image
    actual = json.loads(run('docker', 'image', 'inspect', reference))[0]
    if (actual['Id'], actual['Os'], actual['Architecture']) != (image_id, 'linux', 'amd64'):
        raise ValueError('Cached/pulled image does not match the pinned config and platform')
    if not hit:
        # Save only the image, before any test containers or credentials are used.
        temporary = archive.with_suffix('.partial')
        try:
            run('docker', 'image', 'save', '--output', str(temporary), image_id, timeout=120)
            temporary.replace(archive)
        finally:
            temporary.unlink(missing_ok=True)
    print(f'Transport image cache {"hit" if hit else "miss"}: verified {image_id}', flush=True)
    # Docker archives need not retain RepoDigests. Use the verified content ID,
    # not a restored mutable tag, and keep Compose startup at --pull never.
    return image_id
