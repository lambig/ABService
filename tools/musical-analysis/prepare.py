"""Generate the private, audio-bound sidecar consumed by the offline listening player.

Runs locally with explicitly provided model and Vamp tools. No network or model download.
The Deep Salience network architecture follows Rachel Bittner's MIT-licensed
deepsalience project (see THIRD_PARTY.md); parameters are supplied by the operator.
"""
import argparse
import csv
import hashlib
import io
import json
import math
import os
from pathlib import Path
import re
import subprocess
import tempfile
from importlib.metadata import version

RATE = 22050
HOP = 256
CORE_FRAMES = 1292  # ~15 s core; aligned to the analysis time grid.
CONTEXT_FRAMES = 173  # ~2 s on each side of a core.
LIMIT = 16 * 1024 * 1024
DEPENDENCIES = ('weights', 'host', 'plugin', 'dictionary', 'transform')
QUALITIES = {'': 'major', 'm': 'minor', '7': '7', 'maj7': 'maj7', 'm7': 'm7',
             'dim': 'dim', 'sus4': 'sus4', '5': '5', 'aug': 'aug', 'sus2': 'sus2',
             'm7b5': 'm7b5', '6': '6', 'm6': 'm6'}


def digest(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def canonical_hash(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def pitch_class(note):
    match = re.fullmatch(r'([A-G])([b#]?)', note)
    if not match:
        raise ValueError('Unsupported chord note')
    return ({'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}[match[1]]
            + {'': 0, '#': 1, 'b': -1}[match[2]]) % 12


def harmony_rows(text, duration):
    events = []
    for row in csv.reader(io.StringIO(text)):
        if not row:
            continue
        time, label = float(row[-2]), row[-1]
        if not math.isfinite(time) or time < 0 or time > duration or (events and time < events[-1][0]):
            raise ValueError('Invalid Chordino timestamp')
        match = re.fullmatch(r'([A-G][b#]?)([^/]*)(?:/([A-G][b#]?))?', label)
        if label == 'N':
            value = None
        elif match and match[2] in QUALITIES:
            value = {'root': pitch_class(match[1]), 'quality': QUALITIES[match[2]],
                     'bass': pitch_class(match[3]) if match[3] else None}
        else:
            raise ValueError('Unsupported Chordino label')
        events.append((time, value))
    if not events:
        raise ValueError('Chordino emitted no timeline')
    return [{'start': t, 'end': events[i + 1][0] if i + 1 < len(events) else duration, **v}
            for i, (t, v) in enumerate(events) if v is not None and
            t < (events[i + 1][0] if i + 1 < len(events) else duration)]


def melody_rows(source, weights, duration):
    # Import the heavy inference runtime only for generation, not for contract tests.
    import h5py
    import keras
    import librosa
    import numpy as np
    import soundfile as sf
    import tensorflow as tf
    tf.config.threading.set_intra_op_parallelism_threads(4)
    tf.config.threading.set_inter_op_parallelism_threads(1)
    layers = keras.layers
    inp = layers.Input(shape=(None, None, 6))
    x = layers.BatchNormalization()(inp)
    for count, shape, name in [(128, (5, 5), 'bendy1'), (64, (5, 5), 'bendy2'),
                               (64, (3, 3), 'smoothy1'), (64, (3, 3), 'smoothy2'), (8, (70, 3), 'distribute')]:
        x = layers.Conv2D(count, shape, padding='same', activation='relu', name=name)(x)
        x = layers.BatchNormalization()(x)
    x = layers.Conv2D(1, (1, 1), padding='same', activation='sigmoid', name='squishy')(x)
    model = keras.Model(inp, layers.Lambda(lambda v: tf.squeeze(v, axis=3))(x))
    model.load_weights(str(weights))
    with h5py.File(weights) as f:
        original = []
        for name in f.attrs['layer_names']:
            group = f[name.decode() if isinstance(name, bytes) else name]
            for name in group.attrs['weight_names']:
                original.append(group[name.decode() if isinstance(name, bytes) else name][()])
        if len(original) != len(model.weights) or not all(np.array_equal(a, b.numpy()) for a, b in zip(original, model.weights)):
            raise ValueError('Model weights do not match the fixed architecture')
    rows, active = [], -1
    frames = math.ceil(duration * RATE / HOP)
    # Short accepted previews run in a single window; longer input has bounded context.
    core = frames if duration <= 20 else CORE_FRAMES
    frequencies = librosa.cqt_frequencies(360, fmin=32.7, bins_per_octave=60)
    with sf.SoundFile(source) as audio:
        for start in range(0, frames, core):
            end = min(frames, start + core)
            left = max(0, start - CONTEXT_FRAMES)
            right = min(frames, end + CONTEXT_FRAMES)
            first = round(left * HOP / RATE * audio.samplerate)
            last = min(len(audio), round(right * HOP / RATE * audio.samplerate))
            audio.seek(first)
            y = audio.read(last - first, dtype='float32', always_2d=True).mean(axis=1)
            if not np.isfinite(y).all():
                raise ValueError('Nonfinite PCM')
            y = librosa.resample(y, orig_sr=audio.samplerate, target_sr=RATE)
            cq = [librosa.cqt(y, sr=RATE, hop_length=HOP, fmin=32.7*h, n_bins=360, bins_per_octave=60)
                  for h in [.5, 1, 2, 3, 4, 5]]
            n = min(c.shape[1] for c in cq)
            feature = librosa.amplitude_to_db(np.abs(np.array([c[:, :n] for c in cq])), ref=np.max)/80 + 1
            output = model(feature.transpose(1, 2, 0)[None].astype('float32'), training=False).numpy()[0]
            ids = output.argmax(axis=0)
            for global_frame in range(start, end):
                i = global_frame - left
                if i >= n:
                    raise ValueError('Incomplete melody time grid')
                salience = float(output[ids[i], i])
                midi = float(69 + 12*np.log2(frequencies[ids[i]]/440))
                threshold = .2 if active >= 0 and abs(midi - active) <= .5 else .35
                active = midi if salience >= threshold else -1
                t, stop = global_frame*HOP/RATE, min(duration, (global_frame+1)*HOP/RATE)
                if active >= 0 and t < stop:
                    rows.append({'start': t, 'end': stop, 'midi': midi, 'salience': salience})
            if len(rows) > 200000:
                raise ValueError('Analysis exceeds device event limit')
            print(f'melody {min(duration, end*HOP/RATE):.1f}/{duration:.1f}s', flush=True)
    return rows


def generate(source, config):
    import soundfile as sf
    info = sf.info(source)
    duration = info.frames / info.samplerate
    if not 0 < duration <= 14400 or info.channels not in (1, 2):
        raise ValueError('Unsupported duration or channel count')
    paths = {key: Path(config[key]).resolve(strict=True) for key in DEPENDENCIES}
    fingerprints = {key: digest(path) for key, path in paths.items()}
    if fingerprints != config['sha256']:
        raise ValueError('Engine, model, dictionary or transform changed; verify configuration before generation')
    runtime = {key: version(key) for key in ['numpy', 'scipy', 'librosa', 'tensorflow', 'keras', 'h5py', 'soundfile']}
    if runtime != config['runtime']:
        raise ValueError('Inference runtime changed; verify dependency versions')
    source_hash = digest(source)
    melody = melody_rows(source, paths['weights'], duration)
    env = {**os.environ, 'VAMP_PATH': str(paths['plugin'].parent) + os.pathsep + str(paths['dictionary'].parent)}
    result = subprocess.run([str(paths['host']), '-q', '-t', str(paths['transform']), '-w', 'csv', '--csv-stdout', str(source)],
                            capture_output=True, text=True, env=env, timeout=max(120, math.ceil(duration*2)), check=True)
    harmony = harmony_rows(result.stdout, duration)
    if digest(source) != source_hash or {key: digest(path) for key, path in paths.items()} != fingerprints:
        raise ValueError('Inputs changed during generation')
    generator = digest(__file__)
    engines = {'melody': canonical_hash({'generator': generator, 'runtime': runtime, 'weights': fingerprints['weights'],
                                        'entry': .35, 'hold': .2, 'core': CORE_FRAMES, 'context': CONTEXT_FRAMES}),
               'harmony': canonical_hash({key: fingerprints[key] for key in ['host', 'plugin', 'dictionary', 'transform']})}
    return {'schemaVersion': 1, 'audioSha256': source_hash, 'durationSeconds': duration,
            'analysisVersion': canonical_hash(engines), 'engines': engines, 'melody': melody, 'harmony': harmony}


def write_atomic(value, output):
    body = json.dumps(value, separators=(',', ':'), allow_nan=False).encode()
    if len(body) > LIMIT or len(value['melody']) + len(value['harmony']) > 200000:
        raise ValueError('Analysis exceeds device import limit')
    output.parent.mkdir(parents=True, exist_ok=True)
    name = None
    try:
        with tempfile.NamedTemporaryFile(dir=output.parent, delete=False) as stream:
            name = Path(stream.name)
            stream.write(body)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(name, output)
    finally:
        if name is not None:
            name.unlink(missing_ok=True)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--audio', required=True, type=Path)
    parser.add_argument('--config', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args(argv)
    config = json.loads(args.config.read_text(encoding='utf-8'))
    output = args.output.resolve()
    inputs = [args.audio, args.config, *(Path(config[key]) for key in DEPENDENCIES)]
    if any(output == path.resolve() or (output.exists() and output.samefile(path)) for path in inputs):
        parser.error('Output must not replace an input')
    os.environ.setdefault('TF_CPP_MIN_LOG_LEVEL', '2')
    os.environ.setdefault('TF_ENABLE_ONEDNN_OPTS', '0')
    analysis = generate(args.audio.resolve(strict=True), config)
    write_atomic(analysis, output)
    print('Analysis saved:', len(analysis['melody']), 'melody intervals,', len(analysis['harmony']), 'harmony intervals')


if __name__ == '__main__':
    main()
