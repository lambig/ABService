import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const bytes=readFileSync(new URL('./kernel.wasm',import.meta.url));
async function instance(){
 const {instance:{exports:a}}=await WebAssembly.instantiate(bytes);
 assert.equal(a.initialize(22050),0);
 return {a,input:new Float32Array(a.memory.buffer,a.input_pointer(),a.window_size()),output:new Float64Array(a.memory.buffer,a.output_pointer(),a.output_size())};
}
test('invalid samples reset state and silence never emits a note or chord',async()=>{
 const {a,input,output}=await instance();
 input[0]=NaN;assert.equal(a.analyze(8192),-1);
 assert.equal(output[1],-1);assert.equal(output[4],-1);
 input.fill(0);assert.equal(a.analyze(8704),0);
 assert.equal(output[1],-1);assert.equal(output[4],-1);
 assert.ok([...output].every(Number.isFinite));
 assert.equal(a.initialize(0),-1);assert.equal(a.initialize(Infinity),-1);
 assert.equal(a.analyze(Infinity),-1);
});
test('rewind clears history and produces the same first frame as a new instance',async()=>{
 const first=await instance(),fresh=await instance();
 for(let i=0;i<first.input.length;i++)first.input[i]=Math.sin(2*Math.PI*440*i/22050)*.2;
 fresh.input.set(first.input);first.a.analyze(8192);first.a.analyze(8704);first.a.analyze(8192);fresh.a.analyze(8192);
 assert.deepEqual([...first.output],[...fresh.output]);
});
test('causal refinement never reads future frames; lookahead has explicit bounds',async()=>{
 const left=await instance(),right=await instance(),frames=100,width=left.a.output_size();
 const lp=left.a.sequence_allocate(frames),rp=right.a.sequence_allocate(frames);
 const l=new Float64Array(left.a.memory.buffer,lp,frames*width),r=new Float64Array(right.a.memory.buffer,rp,frames*width);
 for(let f=0;f<frames;f++){l[f*width+24+24]=.8;l[f*width+24+61+24]=1.2;}
 r.set(l);for(let f=50;f<frames;f++){r[f*width+24+28]=2;r[f*width+24+61+28]=3;}
 assert.equal(left.a.sequence_refine(0),0);assert.equal(right.a.sequence_refine(0),0);
 assert.deepEqual([...l.slice(0,50*width)],[...r.slice(0,50*width)]);
 assert.equal(left.a.sequence_refine(27),-1);assert.equal(left.a.sequence_allocate(1501),0);
});
test('bounded lookahead rejects non-finite sequences and stays inside fixed memory',async()=>{
 const {a}=await instance();const size=a.memory.buffer.byteLength,width=a.output_size(),p=a.sequence_allocate(1500);
 assert.notEqual(p,0);const seq=new Float64Array(a.memory.buffer,p,1500*width);seq[0]=NaN;
 assert.equal(a.sequence_refine(26),-1);seq[0]=0;assert.equal(a.sequence_refine(26),0);
 assert.equal(a.memory.buffer.byteLength,size);
});
