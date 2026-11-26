import {execFileSync} from 'node:child_process';
import {copyFileSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const cwd=path.dirname(fileURLToPath(import.meta.url));
const env={...process.env,CARGO_TARGET_DIR:path.join(cwd,'target'),CARGO_ENCODED_RUSTFLAGS:['-C','target-feature=+simd128','-C','link-arg=--initial-memory=8388608','-C','link-arg=--max-memory=8388608'].join('\x1f')};
execFileSync(process.env.CARGO??'cargo',['build','--locked','--release','--target','wasm32-unknown-unknown'],{cwd,env,stdio:'inherit'});
copyFileSync(path.join(cwd,'target/wasm32-unknown-unknown/release/abservice_music_prototype.wasm'),path.join(cwd,'kernel.wasm'));
