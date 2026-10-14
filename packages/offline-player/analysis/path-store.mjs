// SPDX-License-Identifier: GPL-3.0-or-later
const check=signal=>{if(signal?.aborted)throw signal.reason??new DOMException('Cancelled','AbortError');};
export class PathStore {
 constructor(handle,{states,maxFrames,signal,blockFrames=2048}){
  if(!Number.isInteger(states)||states<1||states>65535||!Number.isInteger(maxFrames)||maxFrames<1||maxFrames>310100||!Number.isInteger(blockFrames)||blockFrames<1||blockFrames>2048)throw RangeError('Invalid path dimensions');
  this.handle=handle;this.states=states;this.maxFrames=maxFrames;this.signal=signal;this.blockFrames=blockFrames;
  this.rowBytes=states*2;this.buffer=new Uint8Array(this.rowBytes*blockFrames);this.rows=0;this.buffered=0;this.closed=false;this.sealed=false;
  this.stats={bufferBytes:this.buffer.length,bytesWritten:0,bytesRead:0,writes:0,reads:0};handle.truncate(0);
 }
 guard(){check(this.signal);if(this.closed)throw Error('Path store closed');}
 flush(){this.guard();if(!this.buffered)return;const data=this.buffer.subarray(0,this.buffered*this.rowBytes),offset=(this.rows-this.buffered)*this.rowBytes;
  if(this.handle.write(data,{at:offset})!==data.length)throw Error('Short path write');this.stats.bytesWritten+=data.length;this.stats.writes++;this.buffered=0;
 }
 append(bytes,frame){this.guard();if(this.sealed||frame!==this.rows||frame>=this.maxFrames||bytes.length!==this.rowBytes)throw Error('Invalid path row');
  this.buffer.set(bytes,this.buffered*this.rowBytes);this.buffered++;this.rows++;if(this.buffered===this.blockFrames)this.flush();
 }
 seal(frames){this.guard();if(this.sealed||frames!==this.rows)throw Error('Path frame count mismatch');this.flush();this.handle.flush();this.sealed=true;}
 async trace(lastState,{maxEvents=310101}={}){
  this.guard();if(!this.sealed||!this.rows||!Number.isInteger(lastState)||lastState<0||lastState>=this.states||!Number.isInteger(maxEvents)||maxEvents<2)throw Error('Invalid trace state');
  let state=lastState,loaded=-1;const changes=[];const view=new DataView(this.buffer.buffer);
  for(let frame=this.rows-1;frame>0;frame--){
   this.guard();const first=Math.floor(frame/this.blockFrames)*this.blockFrames;
   if(first!==loaded){const bytes=Math.min(this.blockFrames,this.rows-first)*this.rowBytes;
    if(this.handle.read(this.buffer.subarray(0,bytes),{at:first*this.rowBytes})!==bytes)throw Error('Short path read');
    this.stats.bytesRead+=bytes;this.stats.reads++;loaded=first;await new Promise(r=>setTimeout(r,0));this.guard();
   }
   const previous=view.getUint16((frame-loaded)*this.rowBytes+state*2,true);if(previous>=this.states)throw Error('Invalid backpointer');
   if(previous!==state){changes.push({frame,state});if(changes.length+2>maxEvents)throw Error('Harmony event limit');}state=previous;
  }
  changes.reverse();
  // Preserve upstream oldchord=N initialization even if the first path state differs.
  if(this.rows>1){
   const second=changes[0]?.frame===1?changes.shift().state:state;
   if(second!==this.states-1)changes.unshift({frame:1,state:second});
  }
  const result=[{frame:0,state},...changes,{frame:this.rows-1,state:this.states-1}];if(result.length>maxEvents)throw Error('Harmony event limit');return result;
 }
 close(){if(!this.closed){this.closed=true;this.handle.close();}}
}
export async function createOpfsStore(options){
 check(options.signal);const required=options.states*options.maxFrames*2;
 const estimate=await navigator.storage.estimate();if(Number.isFinite(estimate.quota)&&Number.isFinite(estimate.usage)&&estimate.quota-estimate.usage<required)throw new DOMException('Insufficient path storage','QuotaExceededError');
 const directory=await (await navigator.storage.getDirectory()).getDirectoryHandle('abservice-analysis-scratch-v1',{create:true}),name='harmony-path-'+crypto.randomUUID()+'.tmp';let handle,store;
 try{const file=await directory.getFileHandle(name,{create:true});handle=await file.createSyncAccessHandle();store=new PathStore(handle,options);
  return {store,name,async cleanup(){store.close();await directory.removeEntry(name);}};
 }catch(error){handle?.close();try{await directory.removeEntry(name);}catch{/* Retain the original allocation error; next job cleans any abandoned file. */}throw error;}
}
