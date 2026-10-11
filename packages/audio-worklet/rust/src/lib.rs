//! RustFFT SIMD analysis, privately owned by one AudioWorklet instance.
//! Owns PCM history and FFT scratch. No allocation occurs in push/reset.
//! Fixed 2048/512 analysis, 128-frame input, one or two discrete channels.
use rustfft::{num_complex::Complex, Fft, FftPlannerWasmSimd};
use std::f64::consts::PI;
use std::sync::Arc;

const N: usize = 2048;
const Q: usize = 128;
const CHANNELS: usize = 2;
struct Engine {
    transfer: Vec<f32>,
    ring: Vec<f32>,
    weights: [f64; N],
    fft: Arc<dyn Fft<f64>>,
    buffer: Vec<Complex<f64>>,
    scratch: Vec<Complex<f64>>,
    powers: [f64; N / 2 + 1],
    feature: [f64; 7],
    position: usize,
    filled: usize,
    since: usize,
    channels: usize,
    rate: f64,
    scale: f64,
    expected: f64,
    previous: [f64; 3],
}
impl Engine {
    fn new(rate: f64) -> Self {
        let mut planner = FftPlannerWasmSimd::<f64>::new().expect("WASM SIMD required");
        let fft = planner.plan_fft_forward(N);
        let scratch = vec![Complex::new(0.0, 0.0); fft.get_inplace_scratch_len()];
        let buffer = vec![Complex::new(0.0, 0.0); N];
        let mut e = Self {
            transfer: vec![0.0; Q * CHANNELS],
            ring: vec![0.0; N * CHANNELS],
            weights: [0.0; N],
            fft,
            buffer,
            scratch,
            powers: [0.0; N / 2 + 1],
            feature: [0.0; 7],
            position: 0,
            filled: 0,
            since: 0,
            channels: 0,
            rate,
            scale: 0.0,
            expected: -1.0,
            previous: [0.0; 3],
        };
        for i in 0..N {
            e.weights[i] = 0.5 - 0.5 * (2.0 * PI * i as f64 / N as f64).cos();
            e.scale += e.weights[i] * e.weights[i];
        }
        e.scale *= N as f64;
        e
    }
    fn reset(&mut self) {
        self.position = 0;
        self.filled = 0;
        self.since = 0;
        self.channels = 0;
        self.expected = -1.0;
        self.previous = [0.0; 3];
    }
    fn fft_channel(&mut self, channel: usize) -> f64 {
        let mut squares = 0.0;
        for i in 0..N {
            let value =
                (self.ring[channel * N + ((self.position + i) & (N - 1))] as f64).clamp(-1.0, 1.0);
            squares += value * value;
            self.buffer[i] = Complex::new(value * self.weights[i], 0.0);
        }
        self.fft
            .process_with_scratch(&mut self.buffer, &mut self.scratch);
        for i in 0..=N / 2 {
            self.powers[i] += self.buffer[i].norm_sqr();
        }
        squares / N as f64
    }
    fn push(&mut self, frame: f64, channels: usize) -> i32 {
        if !(1..=CHANNELS).contains(&channels)
            || !frame.is_finite()
            || frame < 0.0
            || frame.fract() != 0.0
            || frame > 9007199254740863.0
            || self.transfer[..channels * Q].iter().any(|v| !v.is_finite())
        {
            self.reset();
            return -1;
        }
        if frame != self.expected || channels != self.channels {
            self.reset();
        }
        self.channels = channels;
        self.expected = frame + Q as f64;
        for i in 0..Q {
            for ch in 0..channels {
                self.ring[ch * N + self.position] = self.transfer[ch * Q + i];
            }
            self.position = (self.position + 1) & (N - 1);
        }
        if self.filled < N {
            self.filled += Q;
            if self.filled < N {
                return 0;
            }
            self.since = 0;
        } else {
            self.since += Q;
            if self.since < 512 {
                return 0;
            }
            self.since = 0;
        }
        self.powers.fill(0.0);
        let mut mean_square = 0.0;
        for ch in 0..channels {
            mean_square += self.fft_channel(ch);
        }
        let mut total = 0.0;
        let mut weighted = 0.0;
        let mut bands = [0.0_f64; 3];
        for i in 0..=N / 2 {
            let hz = i as f64 * self.rate / N as f64;
            let power = self.powers[i] / channels as f64 / self.scale
                * if i == 0 || i == N / 2 { 1.0 } else { 2.0 };
            total += power;
            weighted += hz * power;
            bands[if hz < 250.0 {
                0
            } else if hz < 4000.0 {
                1
            } else {
                2
            }] += power;
        }
        let mut onset = 0.0;
        for (i, value) in bands.iter_mut().enumerate() {
            *value = value.clamp(0.0, 1.0);
            let amplitude = value.sqrt();
            onset += (amplitude - self.previous[i] - 1e-6).max(0.0);
            self.previous[i] = amplitude;
        }
        self.feature = [
            (frame + Q as f64) / self.rate,
            (mean_square / channels as f64).sqrt(),
            bands[0],
            bands[1],
            bands[2],
            (onset / 3.0).min(1.0),
            if total == 0.0 {
                0.0
            } else {
                (weighted / total).min(self.rate / 2.0)
            },
        ];
        1
    }
}

// One single-threaded WASM instance per node; host calls are non-reentrant.
// Host writes transfer memory only between synchronous calls.
static mut ENGINE: *mut Engine = std::ptr::null_mut();
fn engine() -> &'static mut Engine {
    unsafe {
        assert!(!ENGINE.is_null());
        &mut *ENGINE
    }
}
#[no_mangle]
pub extern "C" fn streamInitialize(rate: f64) {
    assert!(rate.is_finite() && rate >= 8000.0 && rate <= 384000.0);
    unsafe {
        if !ENGINE.is_null() {
            drop(Box::from_raw(ENGINE));
            ENGINE = std::ptr::null_mut();
        }
        ENGINE = Box::into_raw(Box::new(Engine::new(rate)));
    }
}
#[no_mangle]
pub extern "C" fn transferPointer() -> *mut f32 {
    engine().transfer.as_mut_ptr()
}
#[no_mangle]
pub extern "C" fn featuresPointer() -> *const f64 {
    engine().feature.as_ptr()
}
#[no_mangle]
pub extern "C" fn reset() {
    engine().reset();
}
#[no_mangle]
pub extern "C" fn push(frame: f64, channels: u32) -> i32 {
    engine().push(frame, channels as usize)
}
#[no_mangle]
pub extern "C" fn dispose() {
    unsafe {
        if !ENGINE.is_null() {
            drop(Box::from_raw(ENGINE));
            ENGINE = std::ptr::null_mut();
        }
    }
}

#[no_mangle]
pub extern "C" fn abiVersion() -> u32 {
    1
}
