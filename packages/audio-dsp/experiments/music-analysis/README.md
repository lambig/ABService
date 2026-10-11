# Musical analysis feasibility experiment

Isolated Rust + RustFFT SIMD experiment for chord root/quality and predominant
melody notes. It is not imported by the player or shipped in its assets.
JavaScript generates fixtures and measures results; both analysis stages run in
Rust/WASM. This experiment does not define the eventual WASM responsibility split.

## Reproduce

Use Rust with the `wasm32-unknown-unknown` target and a working host linker,
and Node.js with WASM SIMD support. The checked lockfile pins dependencies.
The observed build used Rust 1.99.0, Windows GNU, RustFFT 6.4.1.

```
node build.mjs
node --test kernel.test.mjs
node evaluate.mjs results
```

Run from this directory. `CARGO` may name a toolchain-specific executable;
standard Cargo environment variables configure host linking. The build fixes
WASM memory at 8 MiB and enables SIMD. Local PCM, kernels, targets and results
are ignored. Evaluation produces measurements even when musical criteria fail;
exit success is not an acceptance decision.

## Comparison

All modes use an 8192-sample Hann window, 22050 Hz mono and 512-sample hop.
The baseline uses interpolated spectral peaks, harmonic salience, chord templates
and a small previous-pitch preference. Temporal modes use the median per-note
strength over approximately 1.2 seconds as a persistent component, and its
positive residual as a melody candidate. The causal mode only sees past frames;
the lookahead mode sees up to 26 future hops (604 ms).

The analysis time is the FFT window centre: even the causal mode becomes
available approximately 186 ms later. The sequence evaluator is a batch harness,
not a streaming implementation. A lookahead result needs that additional delay
or advance preparation before playback. Boundary error measures timestamps, not
end-to-end display latency; its median must be read with matched/total boundaries.
Confidence is a heuristic margin, not a calibrated probability.

Fixtures contain known notes/chords, rests, harmonic partials, different relative
levels, sustained lead and moving accompaniment. Stable-frame accuracy counts
unknowns as incorrect. The core thresholds were fixed before evaluation: chord
root and quality accuracy >=90%, melody correct octave within 50 cents >=85%,
and matched note-boundary median error <=100 ms. Rest false detections and missed
boundaries remain separate. This small synthetic set is not representative of
all music; inversions, detuning, instruments and dense mixtures need broader
coverage before adoption. No tuning against the counterexamples was performed.

## Finding

Neither hypothesis qualifies for product integration. On the mixed fixture,
chord accuracy is 36.7% / 39.4% / 42.7% for baseline / causal / lookahead;
melody accuracy is 100% / 68.4% / 59.7%. With loud accompaniment, melody accuracy
is 0% / 32.3% / 44.1%. Temporal separation suppresses a sustained lead completely
and mistakes moving accompaniment for melody. Single-part success and fast
desktop processing therefore do not establish mixed-music feasibility.

This rejects these simple hypotheses, not Rust/WASM or music-driven expression.
The next comparison should test pitch-contour tracking and harmonic suppression,
using these counterexamples before production integration. Relevant method
descriptions are [predominant pitch contours](https://essentia.upf.edu/reference/std_PredominantPitchMelodia.html)
and [chord detection from HPCP](https://essentia.upf.edu/reference/std_ChordsDetection.html).
Those are research references; no Essentia implementation is incorporated here.

The raw ABI is test-only: initialize first, reacquire pointers after reinitializing,
fill one complete input window, call analyze with its ending sample index, and
copy the output before the next call. Sequence allocation accepts up to 1500
frames, invalid input is rejected, and output includes internal salience data.
No stable public ABI or Worklet scheduling contract is promised by this experiment.
