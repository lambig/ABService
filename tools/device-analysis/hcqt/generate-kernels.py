import os
from pathlib import Path
ROOT=Path(__file__).resolve().parent
os.environ['NUMBA_CACHE_DIR']=str(ROOT/'cache')
import json,struct,hashlib,importlib
import numpy as np,librosa,soxr
from scipy.signal import convolve
cqmod=importlib.import_module('librosa.core.constantq')
RATE=22050; HOP=256
def u32(stream,*v): stream.write(struct.pack('<'+'I'*len(v),*v))
def f32(stream,v): stream.write(np.array(v,dtype='<f4').tobytes())
filters={};resampling=[]
for factor in [2,4]:
    # Recover the fixed linear HQ decimator's impulse response for each phase.
    center=8192; radius=4096; taps=np.zeros(2*radius+1,dtype=np.float64)
    for phase in range(factor):
        impulse=np.zeros(center*2+1,dtype=np.float32);impulse[center+phase]=1
        response=soxr.resample(impulse,factor,1,quality='HQ')
        for i,v in enumerate(response):
            d=i*factor-center-phase
            if abs(d)<=radius:taps[d+radius]=v
    nz=np.flatnonzero(np.abs(taps)>1e-9); trim=max(radius-nz[0],nz[-1]-radius)
    taps=taps[radius-trim:radius+trim+1].astype(np.float32)
    boundary=[]
    for source in range(64):
        impulse=np.zeros(16384,dtype=np.float32);impulse[source]=1
        response=soxr.resample(impulse,factor,1,quality='HQ')
        for out_index in range(512):
            index=out_index*factor-source+trim
            steady=taps[index] if 0<=index<len(taps) else 0
            correction=float(response[out_index]-steady)
            if abs(correction)>1e-8:boundary.append((out_index,source,correction))
    filters[factor]=(trim,taps,boundary)
    for size in [4097,22050,12345]:
        y=np.random.default_rng(721+size).normal(0,.1,size).astype(np.float32)
        ref=librosa.resample(y,orig_sr=factor,target_sr=1,res_type='soxr_hq',scale=True)
        out=convolve(y.astype(np.float64),taps.astype(np.float64),mode='full',method='direct')[trim:trim+size:factor]
        for oi,si,correction in boundary:
            if oi<len(out) and si<size:out[oi]+=float(y[si])*correction
        out[(size+factor//2)//factor:]=0
        out*=np.sqrt(factor)
        resampling.append({'factor':factor,'samples':size,'radius':int(trim),'boundaryEntries':len(boundary),'maxAbs':float(np.max(np.abs(ref-out))),'rmse':float(np.sqrt(np.mean((ref-out)**2)))})
with (ROOT/'kernels.bin').open('wb') as stream:
    u32(stream,len(filters))
    for factor,(radius,taps,boundary) in filters.items():
        u32(stream,factor,int(radius),len(taps));f32(stream,taps);u32(stream,len(boundary))
        for oi,si,value in boundary:u32(stream,oi,si);f32(stream,[value])
    u32(stream,6)
    for harmonic in [.5,1,2,3,4,5]:
        freqs=librosa.interval_frequencies(360,fmin=32.7*harmonic,intervals='equal',bins_per_octave=60,sort=True)
        alpha=librosa.filters._relative_bandwidth(freqs=freqs)
        _,cutoff=librosa.filters.wavelet_lengths(freqs=freqs,sr=RATE,window='hann',filter_scale=1,gamma=0,alpha=alpha)
        factor=2**cqmod.__early_downsample_count(RATE/2,cutoff,HOP,6)
        assert factor in [1,2,4]
        sr=RATE/factor; hop=HOP//factor
        lengths,_=librosa.filters.wavelet_lengths(freqs=freqs,sr=sr,window='hann',filter_scale=1,gamma=0,alpha=alpha)
        u32(stream,factor,6)
        for octave in range(6):
            start=360-(octave+1)*60;sl=slice(start,start+60)
            basis,nfft,_=cqmod.__vqt_filter_fft(sr/(2**octave),freqs[sl],1,1,.01,window='hann',gamma=0,dtype=np.complex64,alpha=alpha[sl])
            basis[:]*=np.sqrt(2**octave)
            u32(stream,nfft,hop//(2**octave),start,len(basis.data))
            u32(stream,*basis.indptr.tolist());u32(stream,*basis.indices.tolist())
            f32(stream,np.stack([basis.data.real,basis.data.imag],axis=-1));f32(stream,np.sqrt(lengths[sl]))
