import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
const root=fileURLToPath(new URL('.',import.meta.url));
const previous=root;
const zig=process.env.ZIG_EXE; if(!zig)throw Error('Set ZIG_EXE to Zig 0.15.2');
const common=['cc','-target','wasm32-wasi','-mexec-model=reactor','-O3','-g0','-ffp-contract=off','-DNDEBUG','-Wl,--strip-all','-Wl,-z,stack-size=1048576','-Wl,--max-memory=67108864'];
const env={...process.env,ZIG_GLOBAL_CACHE_DIR:path.join(root,'cache'),ZIG_LOCAL_CACHE_DIR:path.join(root,'local-cache')};
const flac=path.join(root,'upstream/flac');
const flacFiles=['bitmath','bitreader','cpu','crc','fixed','format','lpc','md5','memory','stream_decoder'];
const soxr=path.join(previous,'upstream/soxr-a66f3eeeeb62a32403ff143b756eed92b1ec6b62/src');
const builds=[
 {name:'flac',flags:['-DFLAC__NO_ASM','-DFLAC__NO_DLL','-DFLAC__HAS_OGG=0','-DHAVE_FSEEKO=1','-DHAVE_INTTYPES_H=1','-DHAVE_LROUND=1','-DSIZEOF_OFF_T=8','-DPACKAGE_VERSION="1.5.0"','-I'+path.join(flac,'include'),'-I'+path.join(flac,'src/libFLAC/include'),...flacFiles.map(n=>path.join(flac,'src/libFLAC',n+'.c'))],exports:['initialize','dispose','next_block','seek_sample','output','output_length','consume','sample_rate','channel_count','bit_depth','sample_count','finish']},
 {name:'soxr',flags:['-DSOXR_LIB','-I'+previous,'-I'+soxr,...['soxr','data-io','dbesi0','filter','fft4g64','cr','cr32','fft4g32','cr64'].map(n=>path.join(soxr,n+'.c'))],exports:['initialize','dispose','input_ptr','output_ptr','input_used','output_length','process']}
];
const records=[];
for(const b of builds){
 const output=path.join(root,b.name+'.wasm');
 const flags=[...common,...b.flags,path.join(root,b.name+'-adapter.c'),...b.exports.map(n=>'-Wl,--export='+n),'-o',output];
 execFileSync(zig,flags,{env,stdio:'inherit'});
 records.push({name:b.name,flags,sha256:createHash('sha256').update(fs.readFileSync(output)).digest('hex'),bytes:fs.statSync(output).size});
}
fs.writeFileSync(path.join(root,'build.json'),JSON.stringify({compiler:'Zig 0.15.2',builds:records},null,2)+'\n');
console.log(records.map(({name,sha256,bytes})=>({name,sha256,bytes})));
