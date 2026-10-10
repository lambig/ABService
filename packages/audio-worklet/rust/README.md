# RustFFT SIMD audio kernel

The listening media adapter asynchronously compiles this module before creating
its AudioWorklet node. Each node owns one WASM instance. Rust owns PCM history,
Hann coefficients, the FFT plan/scratch, band powers and onset history. The host
copies borrowed PCM into a fixed transfer area and copies each output snapshot.
Media playback, graph lifetime, epochs and notification cadence stay in Web Audio.
The public immutable JavaScript `FeatureStream` remains the numerical reference.

## Contract and failure behavior

- ABI 1; 128 frames, mono/stereo, finite samples, sample rate 8000–384000 Hz.
- 2048-point f64 RustFFT `FftPlannerWasmSimd`, 512-frame hop, existing feature units.
- Gap, channel change, invalid samples and epoch reset discard analysis history.
- Memory is fixed at 2 MiB; initialization allocates, push/reset do not allocate in Rust.
- A node warms its full analysis/accumulation path with 1024 synthetic blocks while
  suspended, then clears all history before reporting ready. Synthetic features
  are never delivered. Preparation remains bounded by the adapter's timeout.
- Unsupported SIMD, CSP, asset, initialization or render contracts report analysis
  failure while preserving direct media output. The UI exposes that failure.
- Disposal detaches the analysis branch, releases the instance when its message is
  processed, and closes the context; a suspended/discarded node is also reclaimed
  with its context. No shared memory or raw PCM crosses a MessagePort.

Precompiling a module off the rendering thread follows the
[AudioWorklet module transfer pattern](https://developer.chrome.com/blog/audio-worklet-design-pattern).
Warm-up reduces initial execution transitions; it does not guarantee a real-time
deadline on every browser/device. Device acceptance includes sustained playback
and simultaneous drawing, separately from numerical/state regression.

## Build and distribution

Install Rust 1.99.0 and target `wasm32-unknown-unknown` (see `rust-toolchain.toml`).
Run `npm run build:wasm -w abservice-audio-worklet` after source changes, then
`npm run check:wasm:rebuild -w abservice-audio-worklet`. Cargo uses the checked-in
lockfile, explicit SIMD, fixed memory and release LTO. `CARGO`/`RUSTC` may select
an already installed toolchain; the build rejects a different compiler version.

The generated binary, notices and source hashes are committed so frontend builds
do not require Rust. `check:wasm` rejects stale source/asset/notice combinations.
Listening CI rebuilds and compares the binary; numerical and processor tests use
that exact asset. Generated paths are remapped to avoid host-specific paths.

The offline shell includes the WASM and third-party notices in its versioned,
hash-checked cache. The listening CSP permits `wasm-unsafe-eval` for compilation;
JavaScript `unsafe-eval` remains disabled. The loader uses ArrayBuffer compilation
so an object store's generic binary MIME type does not prevent startup.

RustFFT and its locked dependencies use the supplied MIT/Apache license texts.
Notices are assembled from `licenses/` and distributed beside the kernel.
