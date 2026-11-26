// SPDX-License-Identifier: MIT
const RATE=22050,HOP=256,CORE=1292,CONTEXT=173;
const pause=()=>new Promise(resolve=>setTimeout(resolve,0));
function check(signal){if(signal?.aborted)throw signal.reason??new DOMException('Cancelled','AbortError');}
async function instantiate(bytes,imports={}){
 let e;
 const view=()=>new DataView(e.memory.buffer);
 const wasi={environ_sizes_get:(p,n)=>{view().setUint32(p,0,true);view().setUint32(n,0,true);return 0;},environ_get:()=>0,fd_close:()=>8,fd_prestat_get:()=>8,fd_prestat_dir_name:()=>8,fd_read:()=>8,fd_seek:()=>8,fd_write:()=>8,proc_exit:code=>{throw Error('WASM exit '+code);}};
 e=(await WebAssembly.instantiate(bytes,{wasi_snapshot_preview1:wasi,...imports})).instance.exports;e._initialize();return e;
}
// Use in a dedicated Worker. Each read allocates at most 64 KiB, including OPFS File snapshots.
export function blobSource(blob){
 const reader=new FileReaderSync();
 return {size:blob.size,read(offset,count){return new Uint8Array(reader.readAsArrayBuffer(blob.slice(offset,offset+count)));}};
}
export async function openFlac(bytes,source,{signal}={}){
 if(!Number.isSafeInteger(source.size)||source.size<42)throw Error('Invalid FLAC size');
 let e,position=0,closed=false,pending=0,cursor=0,sequential=true;
 const stats={bytesRead:0,maxRead:0,readCalls:0,seeks:0,peakWasmBytes:0};
 const guard=()=>{check(signal);if(closed)throw Error('Decoder closed');};
 const imports={input:{
  read(ptr,count){check(signal);const n=Math.min(count,65536,source.size-position);const data=source.read(position,n);
   if(!(data instanceof Uint8Array)||data.length!==n)throw Error('Short input read');
   new Uint8Array(e.memory.buffer,ptr,n).set(data);position+=n;stats.bytesRead+=n;stats.maxRead=Math.max(stats.maxRead,n);stats.readCalls++;return n;},
  seek(value){check(signal);const n=Number(value);if(!Number.isSafeInteger(n)||n<0||n>source.size)return 1;position=n;stats.seeks++;return 0;},
  tell:()=>BigInt(position),length:()=>BigInt(source.size)
 }};
 e=await instantiate(bytes,imports);
 try{check(signal);if(!e.initialize())throw Error('Invalid or unsupported FLAC');}catch(error){e.dispose();throw error;}
 const metadata={rate:e.sample_rate(),channels:e.channel_count(),bits:e.bit_depth(),samples:Number(e.sample_count())};
 function measure(){stats.peakWasmBytes=Math.max(stats.peakWasmBytes,e.memory.buffer.byteLength);}
 measure();
 return {metadata,stats,
  seek(sample){guard();if(!Number.isSafeInteger(sample)||sample<0||sample>=metadata.samples)throw RangeError('Invalid sample position');
   sequential=false;pending=0;cursor=sample;if(!e.seek_sample(BigInt(sample)))throw Error('FLAC seek failed');measure();},
  // Returned copy is valid across further decoder calls. Its size is bounded by one FLAC frame.
  read(max=65535){guard();if(!Number.isInteger(max)||max<1||max>65535)throw RangeError('Invalid block size');
   const status=e.next_block();measure();if(status<0)throw Error('FLAC decode failed');
   if(!status){if(cursor!==metadata.samples)throw Error('Truncated FLAC');return null;}
   const available=e.output_length()-pending;
   const count=Math.min(max,available);if(count<=0||cursor+count>metadata.samples)throw Error('Invalid decoded length');
   const data=new Float32Array(e.memory.buffer,e.output()+pending*4,count).slice();pending+=count;cursor+=count;
   if(pending===e.output_length()){e.consume();pending=0;}return data;},
  finish(){guard();if(!sequential||cursor!==metadata.samples||e.next_block()!==0)throw Error('Sequential EOF required for checksum');if(!e.finish())throw Error('FLAC checksum mismatch');closed=true;e.dispose();measure();},
  close(){if(!closed){closed=true;e.dispose();}}
 };
}
export async function createResampler(bytes,rate,{signal}={}){
 check(signal);const e=await instantiate(bytes);if(!e.initialize(rate))throw Error('Resampler initialization failed');
 let closed=false,ended=false,totalIn=0,totalOut=0;
 const stats={peakWasmBytes:e.memory.buffer.byteLength,maxInput:0,maxOutput:0};
 function guard(){check(signal);if(closed||ended)throw Error('Resampler closed');}
 function output(){const n=e.output_length();stats.maxOutput=Math.max(stats.maxOutput,n);stats.peakWasmBytes=Math.max(stats.peakWasmBytes,e.memory.buffer.byteLength);totalOut+=n;return new Float32Array(e.memory.buffer,e.output_ptr(),n).slice();}
 function* push(data){guard();if(!(data instanceof Float32Array))throw TypeError('Float32 input required');
  for(let offset=0;offset<data.length;){check(signal);const n=Math.min(16384,data.length-offset);stats.maxInput=Math.max(stats.maxInput,n);
   new Float32Array(e.memory.buffer,e.input_ptr(),n).set(data.subarray(offset,offset+n));
   if(!e.process(n,0))throw Error('Resampling failed');const used=e.input_used(),out=output();
   if(!used&&!out.length)throw Error('Resampler stalled');offset+=used;totalIn+=used;if(out.length)yield out;
  }
 }
 function* finish(){guard();ended=true;
  for(;;){check(signal);if(!e.process(0,1))throw Error('Resampler flush failed');const out=output();if(!out.length)break;yield out;}
  const expected=Math.ceil(totalIn*RATE/rate);if(totalOut>expected||expected-totalOut>1)throw Error('Invalid resampled length');
  if(totalOut<expected){totalOut++;yield new Float32Array(1);}
 }
 return {push,finish,stats,close(){if(!closed){closed=true;e.dispose();}}};
}
// Streaming harmony input. Caller consumes blocks; no whole-track PCM is retained.
export async function* harmonyInput(flacBytes,soxrBytes,source,{signal,stats={}}={}){
 const decoder=await openFlac(flacBytes,source,{signal});let resampler;
 try{
  stats.decoder=decoder.stats;const rate=decoder.metadata.rate;
  if(rate!==RATE){resampler=await createResampler(soxrBytes,rate,{signal});stats.resampler=resampler.stats;}
  let blocks=0;
  for(;;){check(signal);const block=decoder.read();if(!block)break;
   if(resampler)yield*resampler.push(block);else yield block;
   if(++blocks%16===0)await pause();
  }
  decoder.finish();if(resampler)yield*resampler.finish();
 }finally{decoder.close();resampler?.close();}
}
// Python round() uses ties-to-even. Preserve the PC operation order before rounding.
export function roundEven(value){const floor=Math.floor(value),fraction=value-floor;return fraction<.5?floor:fraction>.5?floor+1:floor+(floor%2);}
export function* melodyPlan(samples,rate){
 if(!Number.isSafeInteger(samples)||samples<1||!Number.isInteger(rate)||rate<8000||rate>96000||samples>rate*14400)throw RangeError('Invalid input metadata');
 const duration=samples/rate,frames=Math.ceil(duration*RATE/HOP),core=duration<=20?frames:CORE;
 for(let start=0;start<frames;start+=core){const end=Math.min(frames,start+core),left=Math.max(0,start-CONTEXT),right=Math.min(frames,end+CONTEXT);
  yield {start,end,left,right,first:roundEven(left*HOP/RATE*rate),last:Math.min(samples,roundEven(right*HOP/RATE*rate))};
 }
}
// Each window resets soxr, as prepare.py does. HCQT receives the complete window
// and normalizes only that window; the consumer crops CNN rows to [start,end).
export async function* melodyInput(flacBytes,soxrBytes,source,{signal,stats={}}={}){
 const decoder=await openFlac(flacBytes,source,{signal});stats.decoder=decoder.stats;stats.maxWindowSamples=0;
 try{
  const {rate,samples}=decoder.metadata;
  for(const window of melodyPlan(samples,rate)){
   check(signal);decoder.seek(window.first);const expected=Math.ceil((window.last-window.first)*RATE/rate);
   const pcm=new Float32Array(expected);stats.maxWindowSamples=Math.max(stats.maxWindowSamples,expected);
   let resampler,offset=0,remaining=window.last-window.first;
   const copy=block=>{if(offset+block.length>pcm.length)throw Error('Window overflow');pcm.set(block,offset);offset+=block.length;};
   try{
    if(rate!==RATE){resampler=await createResampler(soxrBytes,rate,{signal});stats.resampler=resampler.stats;}
    let blocks=0;
    while(remaining){check(signal);const block=decoder.read(Math.min(65535,remaining));if(!block)throw Error('Truncated window');remaining-=block.length;
     if(resampler)for(const out of resampler.push(block))copy(out);else copy(block);
     if(++blocks%16===0)await pause();
    }
    if(resampler)for(const out of resampler.finish())copy(out);
    if(offset!==expected)throw Error('Window length mismatch');
   }finally{resampler?.close();}
   yield {...window,pcm};await pause();
  }
 }finally{decoder.close();}
}
