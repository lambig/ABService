import fs from 'node:fs';import path from 'node:path';import {fileURLToPath} from 'node:url';import {execFileSync} from 'node:child_process';import {createHash} from 'node:crypto';
const root=fileURLToPath(new URL('.',import.meta.url)),zig=process.env.ZIG_EXE;if(!zig)throw Error('Set ZIG_EXE to pinned Zig 0.15.2');if(execFileSync(zig,['version'],{encoding:'utf8'}).trim()!=='0.15.2')throw Error('Compiler must be 0.15.2');const manifest=JSON.parse(fs.readFileSync(path.join(root,'upstream-files.json')));for(const f of manifest.files){if(createHash('sha256').update(fs.readFileSync(path.join(root,f.path))).digest('hex')!==f.sha256)throw Error('Upstream changed: '+f.path);}
execFileSync(process.execPath,[fileURLToPath(new URL('./generate.mjs',import.meta.url))],{stdio:'inherit'});
execFileSync(process.execPath,['prepare.mjs'],{cwd:root,stdio:'inherit'});fs.mkdirSync(path.join(root,'stream-build'),{recursive:true});
const env={...process.env,ZIG_GLOBAL_CACHE_DIR:path.join(root,'stream-build/cache'),ZIG_LOCAL_CACHE_DIR:path.join(root,'stream-build/local-cache')};
const common=['-target','wasm32-wasi','-O2','-g0','-DNDEBUG','-Iupstream/chordino','-Iupstream/vamp'];
const cpp=['-std=c++17','-fno-exceptions','-D_VAMP_PLUGIN_IN_HOST_NAMESPACE'];
const run=args=>execFileSync(zig,args,{cwd:root,env,stdio:'inherit'});
run(['cc',...common,'-ffast-math','-c','upstream/chordino/nnls.c','-o','stream-build/nnls.o']);
run(['c++',...common,...cpp,'-fno-fast-math','-ffp-contract=off','-c','stream-adapter.cpp','-o','stream-build/adapter.o']);
const files=['Chordino.cpp','NNLSBase.cpp','viterbi.cpp'].map(n=>'upstream/chordino/'+n);
files.push('generated/chromamethods.cpp','stream-core.cpp',...['vamp-sdk/RealTime.cpp','vamp-hostsdk/PluginWrapper.cpp','vamp-hostsdk/PluginInputDomainAdapter.cpp','vamp-hostsdk/PluginBufferingAdapter.cpp'].map(n=>'upstream/vamp/src/'+n));
const flags=['c++',...common,...cpp,'-ffast-math','-mexec-model=reactor',...files,'stream-build/nnls.o','stream-build/adapter.o',...['initialize','push','finish_pass','begin_second','last_state','state_count','frame_count','label','tuning','frame_time','dispose'].map(n=>'-Wl,--export='+n),'-Wl,-z,stack-size=1048576','-Wl,--max-memory=134217728','-Wl,--strip-debug','-o','stream-harmony.wasm'];run(flags);
fs.writeFileSync(path.join(root,'stream-build.json'),JSON.stringify({compiler:'Zig 0.15.2',core:'-O2 -ffast-math',adapter:'-O2 -fno-fast-math -ffp-contract=off',flags,sha256:createHash('sha256').update(fs.readFileSync(path.join(root,'stream-harmony.wasm'))).digest('hex')},null,2)+'\n');
