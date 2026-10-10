// The checked-in asset lets frontend-only builds work without a Rust toolchain.
// CI rebuilds it with the pinned compiler and lockfile before accepting source changes.
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync, mkdirSync, readdirSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const root = fileURLToPath(new URL('.', import.meta.url));
const generated = resolve(root, '../src/generated');
const sha = (bytes) => createHash('sha256').update(bytes).digest('hex');
const licenses = readdirSync(join(root, 'licenses'), { recursive: true, withFileTypes: true })
  .filter((entry) => entry.isFile()).map((entry) => join(entry.parentPath, entry.name)).sort();
const notice = 'Third-party notices for the RustFFT SIMD audio kernel\n\n' + licenses.map((file) =>
  `--- ${file.slice(root.length).replaceAll('\\', '/')} ---\n${readFileSync(file, 'utf8').replaceAll('\r\n', '\n')}\n`).join('\n');
const files = ['Cargo.toml', 'Cargo.lock', 'rust-toolchain.toml', 'src/lib.rs', 'build.mjs', ...licenses.map((file) => file.slice(root.length).replaceAll('\\', '/'))];
const sources = Object.fromEntries(files.map((file) => [file, sha(readFileSync(join(root, file), 'utf8').replaceAll('\r\n', '\n'))]));
const asset = join(generated, 'audio-kernel.wasm');
const metadata = join(generated, 'audio-kernel.json');
const verify = process.argv.includes('--verify');
const check = process.argv.includes('--check');
if (verify) {
  const record = JSON.parse(readFileSync(metadata, 'utf8'));
  assert.deepEqual(record.sources, sources, 'Rust sources changed: run build:wasm');
  assert.equal(sha(readFileSync(asset)), record.sha256, 'WASM asset checksum mismatch');
  assert.equal(readFileSync(join(generated, 'audio-kernel-NOTICES.txt'), 'utf8'), notice, 'Kernel license notices differ');
  console.log('Rust source / WASM asset hashes match');
} else {
  const rustc = process.env.RUSTC || 'rustc';
  const version = execFileSync(rustc, ['--version'], { cwd: root, encoding: 'utf8' }).trim();
  assert.match(version, /^rustc 1\.99\.0 /, 'Use the pinned Rust toolchain');
  const cargo = process.env.CARGO || 'cargo';
  const cargoHome = process.env.CARGO_HOME;
  // Remove host-specific source paths from panic sites in the binary.
  const flags = ['-C', 'target-feature=+simd128', '-C', 'link-arg=--initial-memory=2097152',
    '-C', 'link-arg=--max-memory=2097152', '--remap-path-prefix', `${root.replaceAll('\\', '/')}=/audio-kernel/`,
    ...(cargoHome ? ['--remap-path-prefix', `${cargoHome.replaceAll('\\', '/')}=/cargo`] : [])];
  execFileSync(cargo, ['build', '--manifest-path', join(root, 'Cargo.toml'), '--target', 'wasm32-unknown-unknown', '--release', '--locked'], {
    cwd: root, stdio: 'inherit', env: { ...process.env, CARGO_ENCODED_RUSTFLAGS: flags.join('\x1f') },
  });
  const bytes = readFileSync(join(root, 'target/wasm32-unknown-unknown/release/abservice_audio_kernel.wasm'));
  const record = { compiler: version, target: 'wasm32-unknown-unknown', simd: true, memoryBytes: 2097152, sources, sha256: sha(bytes) };
  if (check) {
    assert.equal(sha(readFileSync(asset)), record.sha256, 'Rebuilt WASM differs from committed asset');
    assert.deepEqual(JSON.parse(readFileSync(metadata, 'utf8')), record, 'Rebuilt WASM metadata differs');
    assert.equal(readFileSync(join(generated, 'audio-kernel-NOTICES.txt'), 'utf8'), notice);
    console.log('Locked Rust rebuild matches committed asset');
  } else {
    mkdirSync(generated, { recursive: true });
    writeFileSync(asset, bytes);
    writeFileSync(metadata, JSON.stringify(record, null, 2) + '\n');
    writeFileSync(join(generated, 'audio-kernel-NOTICES.txt'), notice);
    console.log(`Built audio-kernel.wasm: ${bytes.length} bytes`);
  }
}
