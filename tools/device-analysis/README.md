# Device analysis and corresponding source

The offline player analyzes a saved FLAC locally using the fixed Chordino and
Deep Salience pipelines. No audio is uploaded. Harmony uses two complete scans
and a bounded OPFS backpointer store. Melody uses the existing 22,050 Hz / 256
sample grid, bounded windows and 32-frame CNN tiles. A new result is committed
only after both analyses and audio identity checks succeed. Cancellation and
errors preserve the preceding saved result.

Input limits are 8–96 kHz, mono/stereo, 16/24 bit FLAC, positive known duration
up to four hours. New results use the compact schema 2 below, bounded by four
hours of melody, 200,000 harmony intervals and 16 MiB of JSON. Processing time depends on the
device and input. The management screen displays each phase and supports cancel.

Playback/loading and device analysis are mutually exclusive within the player.
Pause or stop playback before starting analysis. While analysis is queued,
running, saving or cleaning up, play, selection, retry and seeking are blocked.
DSP benchmarks use the same exclusion. Cancellation releases the controls only
after cleanup has completed.

### Compact result contract (schema 2)

Metadata (`audioSha256`, `durationSeconds`, `analysisVersion`, `engines`) retains
its meaning. `melody` and `harmony` are standard base64 strings of little-endian
records, with no header. Schema 1 arrays remain readable with their original
combined 200,000-event limit; newly generated results use schema 2. Older apps
reject schema 2 rather than partially interpreting it. The OPFS namespace,
checksum, atomic replacement and audio-identity checks are unchanged.

- Melody: exactly `ceil(durationSeconds * 22050 / 256)` six-byte records.
  A uint16 bin (0–359) is followed by the original float32 salience (0–1).
  Unknown frames use bin 65535 and salience zero. Frame `i` spans
  `[i * 256 / 22050, min(durationSeconds, (i + 1) * 256 / 22050))`.
  MIDI pitch is `69 + 12 * log2(32.7 / 440) + bin / 5`, as in the accepted
  pipeline. No voiced frames are merged, sampled away or averaged.
- Harmony: at most 200,000 ordered, nonoverlapping 19-byte records.
  Float64 start/end seconds are followed by uint8 root, quality and bass.
  Root/bass range is 0–11; bass 255 means null. Quality indices are, in order,
  `major, minor, 7, maj7, m7, dim, sus4, 5, aug, sus2, m7b5, 6, m6`.
  Gaps remain unknown and interval ends are exclusive.

At four hours, melody contains 1,240,313 frames. Together with 200,000 harmony
intervals, the serialized fixture is 14,989,571 bytes, below 16 MiB. The reader
validates all records once, caches their binary views and reads only the current
melody frame plus a binary search of harmony; it does not expand a million
objects at playback time. Invalid encodings, values, lengths and intervals are
rejected before replacing a saved result.

### Long-input acceptance still required (#290)

The output-capacity defect from #549 is addressed by this contract. Synthetic
four-hour output checks establish value preservation, boundaries and OPFS
save/reload. They do not execute four hours of audio through the DSP engines.
Full long-input analysis resource limits and target Android processing time,
cancellation and playback remain the long-input acceptance work. This does not
reopen the accepted melody/harmony quality decision or block composition review.

## Distribution

The combined PWA containing Chordino is distributed under GPL-3.0-or-later.
Original MIT, BSD, Apache and LGPL notices remain in place; the repository's
MIT license continues to describe its original MIT components. This combination
does not add a warranty, maintenance promise or endorsement by upstream authors.
The licenses' original disclaimers apply.

The PWA build includes `assets/abservice-source.tgz`, `analysis-NOTICES.txt`
and `COPYING-GPL-3.0.txt`. These are versioned and verified by the same offline
shell as the binaries and linked from the management screen. The source archive
contains the public repository sources actually read at build time, bundled npm
runtime package sources and notices, and this directory's native sources,
parameters, upstream archive and build instructions. It includes no deployment
configuration, user audio or private evaluation material.

Use a clean checkout with all intended source files tracked before building;
untracked files are deliberately not included in the source archive. Build
verification checks source and binary identities. A distributor modifying the
program must rebuild both the binary and its corresponding archive together.
An extracted archive builds without Git using its `SOURCE-FILES.json` inventory;
add newly created source files to that inventory when rebuilding outside Git.

## Rebuild

Prerequisites: Node.js 24.22.1 / npm 11.19.1, Python 3.9 or later (zip extraction),
Zig 0.15.2 and Rust 1.99.0 with the wasm32-unknown-unknown target. The recorded
Rust compiler is commit b940084d7eb6a299eb4bfeb8e34901bc051e7ac4, Windows GNU host.
Set `ZIG_EXE`, `RUSTC`, `CARGO` to their executable paths and `CARGO_HOME` to the
Cargo directory. From the repository root:

```sh
node tools/device-analysis/build.mjs
node tools/device-analysis/build.mjs --verify
npm ci
npm run build -w abservice-offline-player
```

The native build extracts and verifies `upstream.zip`, builds with the bundled
Rust dependencies offline, remaps host source paths, and writes the five WASM
assets plus `build.json`. The normal frontend build verifies these assets and
does not require native compilers. `input/build.mjs` and `harmony/build.mjs`
contain the exact C/C++ flags and embedded-dictionary generation steps.

`hcqt/kernels.bin` contains fixed filters, with `generate-kernels.py` as their
generator (librosa 0.11.0, numpy, scipy, soxr 1.1.0). `melody/weights.f32` contains
the Deep Salience melody2 parameters, flattened in layer/weight order into
little-endian float32; architecture and layer order are in `melody/src/lib.rs`.
The original melody2.h5 SHA-256 is
`96ccbfb713f62ded590de4c08ae49a4bd2bfd6c3450e9f85fee51ccae4054fb0`.
The flattened parameters' SHA-256 is
`11356330d842916938e233ead5e7cf6a101d90dbd7a884cfb5f60321377b5b27`.
Parameters and kernels can be replaced and the modules rebuilt with the same ABI.

Upstream provenance: FLAC 1.5.0 commit
1507800de4b70e21be71f38caa0d9079d0bc6e45; libsoxr commit
a66f3eeeeb62a32403ff143b756eed92b1ec6b62; Chordino commit
4c5f214a75cb354f8d8c933e161377c5b4d83713; Vamp SDK 2.10;
Deep Salience <https://github.com/rabitt/DeepSalience>.
The upstream archive retains their source headers and licenses, Zig's runtime
sources, and the fixed RustFFT dependency tree. Native compiler distributions
are installed separately. The source package itself supplies the code linked
into the analysis modules.

## Product checks

`packages/offline-player/e2e/device-analysis.spec.ts` exercises the actual five
WASM modules through offline UI → analysis → atomic save → restart → playback,
and cancellation/cleanup/retry. Existing musical playback tests cover missing
data, seek, pause, loops and item changes. These checks establish integration;
they do not replace Android throughput, long-input product acceptance or the
user's choice of the finished visual composition.
