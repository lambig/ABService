// Fixed-configuration port of the librosa 0.11 CQT projection for Deep Salience.
// Kernels are generated from the pinned PC reference; PCM processing runs here.
use rustfft::{num_complex::Complex32,FftPlanner};
use std::collections::HashMap;
use std::cell::RefCell;
struct Reader { bytes: &'static [u8], pos:usize }
impl Reader {
 fn u(&mut self)->usize {let x=u32::from_le_bytes(self.bytes[self.pos..self.pos+4].try_into().unwrap()) as usize; self.pos+=4;x}
 fn f(&mut self)->f32 {let x=f32::from_le_bytes(self.bytes[self.pos..self.pos+4].try_into().unwrap());self.pos+=4;x}
}
struct Bank { nfft:usize,hop:usize,start:usize, rows:Vec<usize>,indices:Vec<usize>,weights:Vec<Complex32>,scale:Vec<f32> }
struct Harmonic { factor:usize,banks:Vec<Bank> }
struct Decimator { radius:usize,taps:Vec<f32>,boundary:Vec<(usize,usize,f32)> }
struct Config { filters:HashMap<usize,Decimator>,harmonics:Vec<Harmonic> }
fn config()->Config {
 let mut r=Reader{bytes:include_bytes!("../kernels.bin"),pos:0};let mut filters=HashMap::new();
 for _ in 0..r.u(){let factor=r.u();let radius=r.u();let n=r.u();let taps=(0..n).map(|_|r.f()).collect();let boundary=(0..r.u()).map(|_|(r.u(),r.u(),r.f())).collect();filters.insert(factor,Decimator{radius,taps,boundary});}
 let mut harmonics=vec![];
 for _ in 0..r.u(){let factor=r.u();let mut banks=vec![];for _ in 0..r.u(){
   let nfft=r.u();let hop=r.u();let start=r.u();let count=r.u();
   let rows=(0..61).map(|_|r.u()).collect();let indices=(0..count).map(|_|r.u()).collect();
   let weights=(0..count).map(|_|Complex32::new(r.f(),r.f())).collect();let scale=(0..60).map(|_|r.f()).collect();
   banks.push(Bank{nfft,hop,start,rows,indices,weights,scale});
 }harmonics.push(Harmonic{factor,banks});}
 assert_eq!(r.pos,r.bytes.len());Config{filters,harmonics}
}
fn downsample(y:&[f32],factor:usize,c:&Config)->Vec<f32>{
 if factor==1 {return y.to_vec();}
 let d=&c.filters[&factor];let radius=d.radius;let taps=&d.taps;let n=y.len().div_ceil(factor);let valid=(y.len()+factor/2)/factor;
 let gain=(factor as f64).sqrt();let mut result=vec![0_f64;n];
 for (i,v) in result.iter_mut().enumerate().take(valid){let center=i*factor;let begin=center.saturating_sub(radius);let end=y.len().min(center+radius+1);let mut sum=0_f64;
   for j in begin..end{sum+=y[j] as f64*taps[center+radius-j] as f64;}*v=sum;
 }
 for &(oi,si,delta) in &d.boundary {if oi<valid&&si<y.len(){result[oi]+=y[si] as f64*delta as f64;}}
 result.into_iter().map(|v|(v*gain) as f32).collect()
}
fn hcqt(pcm:&[f32])->Vec<f32>{
 let c=config();let frames=pcm.len()/256+1;let mut out=vec![0_f32;360*frames*6];
 let mut planner=FftPlanner::<f32>::new();
 for (h,harmonic) in c.harmonics.iter().enumerate(){
   let mut y=downsample(pcm,harmonic.factor,&c);
   for (octave,b) in harmonic.banks.iter().enumerate(){
     if octave>0 {y=downsample(&y,2,&c);}
     let fft=planner.plan_fft_forward(b.nfft);let mut window=vec![Complex32::new(0.,0.);b.nfft];let mut scratch=vec![Complex32::new(0.,0.);fft.get_inplace_scratch_len()];
     assert!(y.len()/b.hop+1>=frames);
     for t in 0..frames{
       for (i,v) in window.iter_mut().enumerate(){let j=(t*b.hop+i) as isize-b.nfft as isize/2;*v=Complex32::new(if j>=0&&(j as usize)<y.len(){y[j as usize]}else{0.},0.);}
       fft.process_with_scratch(&mut window,&mut scratch);
       for row in 0..60{let mut v=Complex32::new(0.,0.);for k in b.rows[row]..b.rows[row+1]{v+=b.weights[k]*window[b.indices[k]];}
         out[((b.start+row)*frames+t)*6+h]=(v/b.scale[row]).norm();
       }
     }
   }
 }
 let peak=out.iter().copied().fold(0_f32,f32::max);let reference=10.*(peak*peak).max(1e-10).log10();
 for v in &mut out{let db=10.*(*v**v).max(1e-10).log10()-reference;*v=db.max(-80.)/80.+1.;}
 out
}
struct State { input:Vec<f32>,output:Vec<f32> }
thread_local! { static STATE: RefCell<Option<State>> = const { RefCell::new(None) }; }
#[no_mangle]
pub extern "C" fn initialize(samples:usize)->usize{
 if samples==0||samples>22050*60{return 0;}
 let mut state=State{input:vec![0.;samples],output:vec![]};
 let pointer=state.input.as_mut_ptr() as usize;
 STATE.with(|slot| *slot.borrow_mut()=Some(state));
 pointer
}
#[no_mangle]
pub extern "C" fn infer()->usize{STATE.with(|slot| {
 let mut value=slot.borrow_mut();
 let Some(state)=value.as_mut() else {return 0;};
 state.output=hcqt(&state.input);state.output.as_ptr() as usize
})}
#[no_mangle]
pub extern "C" fn dispose(){STATE.with(|slot| *slot.borrow_mut()=None);}
