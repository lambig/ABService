import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
const dir=path.dirname(fileURLToPath(import.meta.url));
const bytes=fs.readFileSync(path.join(dir,'kernel.wasm'));
const module=await WebAssembly.compile(bytes);
export const rate=22050, hop=512;
const hz=m=>440*2**((m-69)/12);
const chordNotes=[[48,52,55],[45,48,52],[53,57,60],[43,47,50]];
const roots=[0,9,5,7],qualities=[0,1,0,0];
const melody=[72,74,76,79,77,76,74,-1,72,76,79,81,79,76,74,-1];
const envelope=(t,length)=>Math.min(1,t/.025,(length-t)/.025);
export function fixture(name){
  const duration=16,pcm=new Float32Array(duration*rate);
  const isMelody=!['chords','sevenths','silence'].includes(name);
  const isChord=!['melody','silence'].includes(name);
  const loud=name==='loud-accompaniment';
  const expectedNote=t=>name==='sustained-lead'?76:melody[Math.floor(t/.5)%melody.length];
  for(let i=0;i<pcm.length;i++){
    const t=i/rate,c=Math.floor(t/4), n=expectedNote(t);
    if(isChord){
      const notes=name==='sevenths'?[...chordNotes[c],chordNotes[c][0]+10]:chordNotes[c];
      const selected=name==='moving-accompaniment'?[notes[Math.floor(t/.25)%notes.length]]:notes;
      for(const note of selected){for(let h=1;h<=4;h++)pcm[i]+=(loud?.12:.045)/h*Math.sin(2*Math.PI*hz(note)*h*t)*envelope(t%(name==='moving-accompaniment'?.25:4),name==='moving-accompaniment'?.25:4);}
    }
    if(isMelody&&n>=0)for(let h=1;h<=5;h++)pcm[i]+=(loud?.06:.2)/h*Math.sin(2*Math.PI*hz(n)*h*t)*envelope(t%(name==='sustained-lead'?16:.5),name==='sustained-lead'?16:.5);
  }
  return {name,pcm,duration,truth:t=>({
    note:isMelody?expectedNote(t):-1,
    root:isChord?roots[Math.floor(t/4)]:-1,
    quality:isChord?(name==='sevenths'?(qualities[Math.floor(t/4)]===1?4:2):qualities[Math.floor(t/4)]):-1,
    stableChord:t%4>.45&&t%4<3.75,
    stableMelody:t%.5>.13&&t%.5<.37,
  })};
}
export async function analyze(pcm,sampleRate=rate,mode='baseline'){
  const api=(await WebAssembly.instantiate(module)).exports;
  if(api.initialize(sampleRate)!==0)throw Error('Unsupported rate');
  const n=api.window_size(),input=new Float32Array(api.memory.buffer,api.input_pointer(),n);
  const width=api.output_size(),output=new Float64Array(api.memory.buffer,api.output_pointer(),width);
  const vectors=[],times=[];
  for(let end=n;end<=pcm.length;end+=hop){
    const started=performance.now();input.set(pcm.subarray(end-n,end));const status=api.analyze(end);
    if(status<0)throw Error('Invalid PCM');
    times.push(performance.now()-started);
    vectors.push([...output]);
  }
  let temporalMs=0;
  if(mode!=='baseline'){
    if(!['causal','lookahead'].includes(mode))throw Error('Unknown mode');
    const pointer=api.sequence_allocate(vectors.length);if(!pointer)throw Error('Sequence exceeds fixed bound');
    const sequence=new Float64Array(api.memory.buffer,pointer,vectors.length*width);sequence.set(vectors.flat());
    const started=performance.now();if(api.sequence_refine(mode==='lookahead'?26:0)!==0)throw Error('Sequence invalid');temporalMs=performance.now()-started;
    for(let i=0;i<vectors.length;i++)vectors[i]=[...sequence.slice(i*width,(i+1)*width)];
  }
  const rows=vectors.map(o=>({time:o[0],availableAt:o[0]+n/sampleRate/2+(mode==='lookahead'?26*hop/sampleRate:0),note:o[1],pitchHz:o[2],melodyConfidence:o[3],root:o[4],quality:o[5],chordConfidence:o[6],rms:o[7],chroma:o.slice(8,20)}));
  times.sort((a,b)=>a-b);
  return {rows,timing:{frames:times.length,p50ms:times[Math.floor(times.length*.5)],p95ms:times[Math.floor(times.length*.95)],maxMs:times.at(-1),totalMs:times.reduce((a,b)=>a+b,0),temporalMs,hopMs:hop/sampleRate*1000,lookbackLatencyMs:n/sampleRate/2*1000,extraLookaheadMs:mode==='lookahead'?26*hop/sampleRate*1000:0}};
}
export function score(data,truth){
  let chordTotal=0,chordCorrect=0,chordUnknown=0,noteTotal=0,noteCorrect=0,noteUnknown=0,unvoiced=0,falseVoice=0;
  for(const r of data.rows){const t=truth(r.time);
    if(t.root>=0&&t.stableChord){chordTotal++;chordCorrect+=r.root===t.root&&r.quality===t.quality?1:0;chordUnknown+=r.root<0?1:0;}
    if(t.stableMelody&&t.note>=0){noteTotal++;noteCorrect+=r.note>=0&&Math.abs(r.note-t.note)<=.5?1:0;noteUnknown+=r.note<0?1:0;}
    if(t.note<0&&t.stableMelody){unvoiced++;falseVoice+=r.note>=0?1:0;}
  }
  const targets=[];
  for(let t=.5;t<data.rows.at(-1).time;t+=.5){if(truth(t+.01).note>=0&&truth(t+.01).note!==truth(t-.01).note)targets.push({time:t,note:truth(t+.01).note});}
  const transitions=data.rows.filter((r,i)=>i>0&&r.note>=0&&Math.round(r.note)!==Math.round(data.rows[i-1].note));
  const used=new Set(),errors=[];
  for(const target of targets){const matches=transitions.map((r,i)=>({r,i})).filter(({r,i})=>!used.has(i)&&Math.abs(r.note-target.note)<=.5&&Math.abs(r.time-target.time)<.3).sort((a,b)=>Math.abs(a.r.time-target.time)-Math.abs(b.r.time-target.time));if(matches.length){used.add(matches[0].i);errors.push(Math.abs(matches[0].r.time-target.time)*1000);}}
  errors.sort((a,b)=>a-b);
  return {chordTotal,chordAccuracy:chordTotal?chordCorrect/chordTotal:null,chordUnknown:chordTotal?chordUnknown/chordTotal:null,noteTotal,noteAccuracy:noteTotal?noteCorrect/noteTotal:null,noteUnknown:noteTotal?noteUnknown/noteTotal:null,unvoiced,falseVoiceRate:unvoiced?falseVoice/unvoiced:null,noteBoundaries:targets.length,matchedBoundaries:errors.length,matchedBoundaryMedianErrorMs:errors.length?errors[Math.floor(errors.length/2)]:null};
}
if(process.argv[1]===fileURLToPath(import.meta.url)){
  const results=path.resolve(process.argv[2]??path.join(dir,'results'));fs.mkdirSync(results,{recursive:true});
  const summary={schemaVersion:1,kernelSha256:createHash('sha256').update(bytes).digest('hex'),thresholds:{chordAccuracy:.9,noteAccuracy:.85},runtime:'Node WASM SIMD; not Android evidence',cases:[]};
  for(const name of ['chords','sevenths','melody','accompanied-melody','loud-accompaniment','silence','sustained-lead','moving-accompaniment']){
    const f=fixture(name);
    for(const mode of ['baseline','causal','lookahead']){
      const result=await analyze(f.pcm,rate,mode),metrics=score(result,f.truth);
      fs.writeFileSync(path.join(results,name+'-'+mode+'.json'),JSON.stringify({name,mode,...result,metrics}));
      summary.cases.push({name,mode,...metrics,timing:result.timing});
    }
  }
  fs.writeFileSync(path.join(results,'summary.json'),JSON.stringify(summary,null,2)+'\n');console.log(JSON.stringify(summary,null,2));
}
