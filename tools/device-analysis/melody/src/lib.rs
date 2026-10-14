// Limited fixed-model feasibility adapter, not the product inference API.
// Architecture/weights: Deep Salience, Copyright 2017 Rachel Bittner (MIT).
use std::arch::wasm32::*;
const HEIGHT: usize = 360;
struct State { frames: usize, input: Vec<f32>, output: Vec<f32>, weights: Vec<f32> }
static mut STATE: *mut State = std::ptr::null_mut();

#[no_mangle]
pub unsafe extern "C" fn initialize(frames: usize) -> usize {
    if frames == 0 || frames > 128 { return 0; }
    dispose();
    let weights = include_bytes!("../weights.f32").chunks_exact(4)
        .map(|b| f32::from_le_bytes(b.try_into().unwrap())).collect();
    STATE = Box::into_raw(Box::new(State {frames,input:vec![0.;HEIGHT*frames*6],output:vec![],weights}));
    (*STATE).input.as_mut_ptr() as usize
}
#[no_mangle]
pub unsafe extern "C" fn dispose() {
    if !STATE.is_null() { drop(Box::from_raw(STATE)); STATE=std::ptr::null_mut(); }
}
fn normalize(x: &mut [f32], channels: usize, w: &[f32], pos: &mut usize) {
    let p=*pos; *pos+=channels*4;
    for pixel in x.chunks_exact_mut(channels) {
        for c in 0..channels {
            let scale=w[p+c]/(w[p+channels*3+c]+0.001).sqrt();
            pixel[c]=(pixel[c]-w[p+channels*2+c])*scale+w[p+channels+c];
        }
    }
}
fn conv(input: &[f32], frames: usize, ic: usize, oc: usize, kh: usize, kw: usize, w: &[f32], pos: &mut usize, sigmoid: bool) -> Vec<f32> {
    let offset=*pos; let count=kh*kw*ic*oc; *pos+=count+oc;
    let kernel=&w[offset..offset+count]; let bias=&w[offset+count..offset+count+oc];
    let mut out=vec![0.;HEIGHT*frames*oc];
    let ph=(kh-1)/2; let pt=(kw-1)/2;
    for h in 0..HEIGHT { for t in 0..frames {
        let dst=&mut out[(h*frames+t)*oc..(h*frames+t+1)*oc]; dst.copy_from_slice(bias);
        for y in 0..kh {
            let ih=h as isize+y as isize-ph as isize;
            if ih<0 || ih>=HEIGHT as isize {continue;}
            for z in 0..kw {
                let it=t as isize+z as isize-pt as isize;
                if it<0 || it>=frames as isize {continue;}
                let src=&input[(ih as usize*frames+it as usize)*ic..][..ic];
                let base=(y*kw+z)*ic*oc;
                for c in 0..ic {
                    let k=&kernel[base+c*oc..][..oc];
                    if oc>=4 { unsafe {
                        let value=f32x4_splat(src[c]);
                        for o in (0..oc).step_by(4) {
                            let a=v128_load(dst.as_ptr().add(o) as *const v128);
                            let b=v128_load(k.as_ptr().add(o) as *const v128);
                            v128_store(dst.as_mut_ptr().add(o) as *mut v128,f32x4_add(a,f32x4_mul(value,b)));
                        }
                    }} else { dst[0]+=src[c]*k[0]; }
                }
            }
        }
        for v in dst { *v=if sigmoid {1./(1.+(-*v).exp())} else {v.max(0.)}; }
    }}
    out
}
#[no_mangle]
pub unsafe extern "C" fn infer() -> usize {
    if STATE.is_null() {return 0;}
    let s=&mut *STATE; let mut p=0; let mut x=s.input.clone();
    normalize(&mut x,6,&s.weights,&mut p);
    let mut ic=6;
    for (oc,kh,kw) in [(128,5,5),(64,5,5),(64,3,3),(64,3,3),(8,70,3)] {
        x=conv(&x,s.frames,ic,oc,kh,kw,&s.weights,&mut p,false);
        normalize(&mut x,oc,&s.weights,&mut p); ic=oc;
    }
    s.output=conv(&x,s.frames,8,1,1,1,&s.weights,&mut p,true);
    assert_eq!(p,s.weights.len());
    s.output.as_ptr() as usize
}
