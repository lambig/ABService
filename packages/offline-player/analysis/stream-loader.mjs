// SPDX-License-Identifier: GPL-3.0-or-later
const check=signal=>{if(signal?.aborted)throw signal.reason??new DOMException('Cancelled','AbortError');};
export async function analyzeStream(bytes,{samples,inputFactory,storeFactory,signal,progress=()=>{}}){
 if(!Number.isInteger(samples)||samples<1||samples>22050*14400)throw RangeError('Invalid sample count');
 let e,resource;const stats={peakWasmBytes:0,passes:[]};
 const memory=()=>new DataView(e.memory.buffer),wasi={environ_sizes_get:(n,s)=>{memory().setUint32(n,0,true);memory().setUint32(s,0,true);return 0;},environ_get:()=>0,fd_close:()=>8,fd_read:()=>8,fd_fdstat_get:()=>8,fd_seek:()=>8,fd_write:()=>8,proc_exit:c=>{throw Error('Harmony exit '+c);}};
 const path_store={append:(ptr,states,frame)=>{check(signal);if(!resource||states!==e.state_count())throw Error('Path store missing');resource.store.append(new Uint8Array(e.memory.buffer,ptr,states*2),frame);return 0;}};
 try{
  check(signal);e=(await WebAssembly.instantiate(bytes,{wasi_snapshot_preview1:wasi,path_store})).instance.exports;e._initialize();
  const ptr=e.initialize(samples);if(!ptr)throw Error('Harmony initialization failed');const states=e.state_count();
  resource=await storeFactory({states,maxFrames:Math.ceil(samples/1024)+8,signal});
  for(let pass=1;pass<=2;pass++){
   if(pass===2&&e.begin_second()!==0)throw Error('Second pass initialization failed');let total=0,calls=0;
   for await(const block of inputFactory(pass)){
    check(signal);if(!(block instanceof Float32Array))throw TypeError('Float32 input required');
    for(let offset=0;offset<block.length;offset+=16384){check(signal);const n=Math.min(16384,block.length-offset);new Float32Array(e.memory.buffer,ptr,n).set(block.subarray(offset,offset+n));if(e.push(n)!==0)throw Error('Harmony frame processing failed');total+=n;stats.peakWasmBytes=Math.max(stats.peakWasmBytes,e.memory.buffer.byteLength);if(++calls%16===0){progress({pass,samples:total,total:samples});await new Promise(r=>setTimeout(r,0));}}
   }
   if(total!==samples)throw Error('Incomplete harmony input');const frames=e.finish_pass();if(frames<1)throw Error('Harmony pass finalization failed');stats.peakWasmBytes=Math.max(stats.peakWasmBytes,e.memory.buffer.byteLength);
   stats.passes.push({samples:total,frames,tuning:[...new Float32Array(e.memory.buffer,e.tuning(),3)]});progress({pass,samples:total,total:samples});
  }
  const frames=e.frame_count();resource.store.seal(frames);const transitions=await resource.store.trace(e.last_state());
  const labels=[];for(let n=0;n<states;n++){const p=e.label(n),b=new Uint8Array(e.memory.buffer);let end=p;while(b[end])end++;labels.push(new TextDecoder().decode(b.subarray(p,end)));}
  const events=transitions.map(({frame,state})=>({time:e.frame_time(frame),label:labels[state]}));check(signal);
  return {events,stats:{...stats,states,path:resource.store.stats}};
 }finally{e?.dispose();await resource?.cleanup();}
}
