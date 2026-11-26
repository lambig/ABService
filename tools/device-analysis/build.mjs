// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFileSync,writeFileSync,readdirSync,mkdirSync,copyFileSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
const root=fileURLToPath(new URL('.',import.meta.url));
const assets=path.resolve(root,'../../packages/offline-player/analysis');
const hash=data=>createHash('sha256').update(data).digest('hex');
const names=['flac.wasm','soxr.wasm','stream-harmony.wasm','hcqt.wasm','melody.wasm'];
const paths=['build.mjs','upstream.zip',...['input','harmony','hcqt','melody','rust-notices'].flatMap(dir=>readdirSync(path.join(root,dir),{recursive:true,withFileTypes:true}).filter(e=>e.isFile()).map(e=>path.relative(root,path.join(e.parentPath,e.name)).replaceAll('\\','/')))]
  .filter(p=>!/(?:^|\/)(?:target|cache|local-cache|stream-build|generated|upstream|runtime-source)(?:\/|$)/.test(p)&&!/(?:^|\/)(?:build|stream-build)\.json$/.test(p)&&!p.endsWith('.wasm')&&p!=='harmony/stream-core.cpp').sort();
const sources=Object.fromEntries(paths.map(p=>{const bytes=readFileSync(path.join(root,p));return [p,hash(/\.(bin|f32|zip)$/.test(p)?bytes:bytes.toString('utf8').replaceAll('\r\n','\n'))];}));
const record=()=>({sources,assets:Object.fromEntries(names.map(p=>[p,hash(readFileSync(path.join(assets,p)))]))});
if(process.argv.includes('--verify')){
  assert.deepEqual(record(),JSON.parse(readFileSync(path.join(root,'build.json'))),'Analysis source or WASM changed; rebuild the modules');
  console.log('Analysis sources and five WASM assets match');
}else{
  const archive=JSON.parse(readFileSync(path.join(root,'upstream.json')));
  assert.equal(hash(readFileSync(path.join(root,'upstream.zip'))),archive.sha256);
  execFileSync(process.env.PYTHON??'python',['-m','zipfile','-e',path.join(root,'upstream.zip'),root],{stdio:'inherit'});
  for(const file of archive.files)assert.equal(hash(readFileSync(path.join(root,file.path))),file.sha256,file.path);
  const lock=readFileSync(path.join(root,'hcqt/Cargo.lock'),'utf8');
  for(const block of lock.split('[[package]]').slice(1)){
    const name=/name = "([^"]+)"/.exec(block)?.[1],version=/version = "([^"]+)"/.exec(block)?.[1],checksum=/checksum = "([^"]+)"/.exec(block)?.[1];
    if(!checksum)continue;
    const prefix=`vendor/${name}-${version}/`;
    const files=Object.fromEntries(archive.files.filter(f=>f.path.startsWith(prefix)).map(f=>[f.path.slice(prefix.length),f.sha256]));
    writeFileSync(path.join(root,prefix,'.cargo-checksum.json'),JSON.stringify({package:checksum,files}));
  }
  const rustc=process.env.RUSTC??'rustc',cargo=process.env.CARGO??'cargo';
  assert.match(execFileSync(rustc,['--version'],{encoding:'utf8'}),/^rustc 1\.99\.0 /);
  const cargoHome=process.env.CARGO_HOME;assert.ok(cargoHome,'Set CARGO_HOME');
  mkdirSync(path.join(root,'.cargo'),{recursive:true});
  writeFileSync(path.join(root,'.cargo/config.toml'),'[source.crates-io]\nreplace-with = "vendored-sources"\n[source.vendored-sources]\ndirectory = "vendor"\n');
  for(const [dir,name] of [['hcqt','hcqt_port'],['melody','device_melody_feasibility']]){
    const flags=['-C','target-feature=+simd128','-C','link-arg=--max-memory=268435456','--remap-path-prefix',`${root.replaceAll('\\','/')}=/analysis/`,'--remap-path-prefix',`${cargoHome.replaceAll('\\','/')}=/cargo`];
    execFileSync(cargo,['build','--manifest-path',path.join(root,dir,'Cargo.toml'),'--target','wasm32-unknown-unknown','--release','--locked','--offline'],{cwd:root,stdio:'inherit',env:{...process.env,CARGO_ENCODED_RUSTFLAGS:flags.join('\x1f')}});
    copyFileSync(path.join(root,dir,'target/wasm32-unknown-unknown/release',name+'.wasm'),path.join(assets,dir+'.wasm'));
  }
  for(const dir of ['input','harmony'])execFileSync(process.execPath,[path.join(root,dir,'build.mjs')],{cwd:path.join(root,dir),stdio:'inherit'});
  for(const [dir,name] of [['input','flac.wasm'],['input','soxr.wasm'],['harmony','stream-harmony.wasm']])copyFileSync(path.join(root,dir,name),path.join(assets,name));
  writeFileSync(path.join(root,'build.json'),JSON.stringify(record(),null,2)+'\n');
}
