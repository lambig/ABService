# Streaming input source

This directory contains the source, adapters, build script and license notices
needed to rebuild flac.wasm and soxr.wasm. No input audio or deployment settings
are needed to build these modules.

* FLAC 1.5.0: https://github.com/xiph/flac/commit/1507800de4b70e21be71f38caa0d9079d0bc6e45
  Archive SHA-256: d80ef5facdb21972efe91774da03d6b9abf216aa17093d740fcc411cd8afbb41.
  The decoder library's BSD license is in upstream/flac/COPYING.Xiph;
  the source tree retains licenses for the other upstream tools as well.
* libsoxr: https://github.com/chirlu/soxr/commit/a66f3eeeeb62a32403ff143b756eed92b1ec6b62
  Archive SHA-256: def11f2bbc4777662c2d33d2c46a13b1c7b9ce78c8b03260cd55026a62a6fc20.
  LGPL-2.1-or-later; the complete upstream license and source are included.
  The stateful soxr adapter is provided under the same terms.
* Build tool: Zig 0.15.2, https://ziglang.org/download/0.15.2/zig-x86_64-windows-0.15.2.zip
  Windows archive SHA-256: 3a0ed1e8799a2f8ce2a6e6290a9ff22e6906f8227865911fb7ddedc3cc14cb0c.

With Node.js and the fixed Zig compiler installed, set ZIG_EXE to the compiler
executable and run `node build.mjs` from this directory. The build reads only
the included sources and writes two WASM files and build.json. Generated
build.json records the exact arguments and binary hashes. Memory maximum is
64 MiB per module; the tests measure actual use separately.

The upstream source is unmodified. flac-adapter.c selects a bounded mono float32
output from libFLAC read/seek callbacks. soxr-adapter.c selects HQ float32 mono,
one runtime thread and bounded input/output buffers. C floating-point contraction
is disabled. Replacing either source or adapter and rebuilding produces a
replaceable module with the same exported ABI. The modules have no host filesystem,
network or environment access; input bytes are supplied by the JS adapter.

Retain the existing project MIT notices and upstream notices when assembling the
PWA. This component source package does not by itself constitute the complete
corresponding source of the PWA including Chordino and other modules.

The licenses include their original warranty disclaimers. No additional warranty
or support promise is introduced by these adapters.
