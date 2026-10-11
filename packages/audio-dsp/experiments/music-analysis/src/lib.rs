//! Experimental tonal analysis, isolated from production playback.
//! Fixed mono window. RustFFT SIMD, spectral peaks, harmonic salience,
//! chord templates and causal pitch continuity. Confidence is a heuristic,
//! not a calibrated probability. No inference of track identity or emotion.
use rustfft::{num_complex::Complex, Fft, FftPlannerWasmSimd};
use std::{cell::RefCell, f64::consts::PI, sync::Arc};

const N: usize = 8192;
const LOW: usize = 36;
const NOTES: usize = 61;
const OUT: usize = 24 + NOTES * 2;
const CHORDS: [&[usize]; 7] = [
    &[0, 4, 7],
    &[0, 3, 7],
    &[0, 4, 7, 10],
    &[0, 4, 7, 11],
    &[0, 3, 7, 10],
    &[0, 3, 6],
    &[0, 5, 7],
];

struct Analyzer {
    input: [f32; N],
    fft: Arc<dyn Fft<f64>>,
    buffer: Vec<Complex<f64>>,
    scratch: Vec<Complex<f64>>,
    magnitudes: [f64; N / 2 + 1],
    window: [f64; N],
    output: [f64; OUT],
    previous_pitch: f64,
    previous_frame: f64,
    chroma: [f64; 12],
    rate: f64,
}

impl Analyzer {
    fn new(rate: f64) -> Self {
        let fft = FftPlannerWasmSimd::<f64>::new()
            .unwrap()
            .plan_fft_forward(N);
        let scratch = vec![Complex::default(); fft.get_inplace_scratch_len()];
        Self {
            input: [0.; N],
            fft,
            buffer: vec![Complex::default(); N],
            scratch,
            magnitudes: [0.; N / 2 + 1],
            window: std::array::from_fn(|i| 0.5 - 0.5 * (2. * PI * i as f64 / N as f64).cos()),
            output: [0.; OUT],
            previous_pitch: 0.,
            previous_frame: -1.,
            chroma: [0.; 12],
            rate,
        }
    }
    fn reset(&mut self) {
        self.previous_pitch = 0.;
        self.previous_frame = -1.;
        self.chroma.fill(0.);
        self.output.fill(0.);
        self.output[1] = -1.;
        self.output[4] = -1.;
        self.output[5] = -1.;
    }
    fn run(&mut self, end_frame: f64) -> i32 {
        if !end_frame.is_finite()
            || end_frame < N as f64
            || self.input.iter().any(|x| !x.is_finite())
        {
            self.reset();
            return -1;
        }
        if self.previous_frame >= end_frame {
            self.reset();
        }
        let dt = if self.previous_frame < 0. {
            N as f64 / self.rate
        } else {
            (end_frame - self.previous_frame) / self.rate
        };
        self.previous_frame = end_frame;
        self.output.fill(0.);
        self.output[0] = (end_frame - N as f64 / 2.) / self.rate;
        self.output[1] = -1.;
        self.output[4] = -1.;
        self.output[5] = -1.;
        let rms = (self.input.iter().map(|x| (*x as f64).powi(2)).sum::<f64>() / N as f64).sqrt();
        self.output[7] = rms;
        if rms < 1e-5 {
            self.previous_pitch = 0.;
            self.chroma.fill(0.);
            return 0;
        }
        for i in 0..N {
            self.buffer[i] = Complex::new(self.input[i] as f64 * self.window[i], 0.);
        }
        self.fft
            .process_with_scratch(&mut self.buffer, &mut self.scratch);
        for i in 0..=N / 2 {
            self.magnitudes[i] = self.buffer[i].norm();
        }
        let max = self.magnitudes.iter().copied().fold(0., f64::max);
        let mut pitches = [0.; NOTES];
        let mut fundamental = [0.; NOTES];
        let mut pitch_sum = [0.; NOTES];
        // Interpolated local maxima; peaks vote for fundamental candidates.
        for k in 2..N / 2 {
            let m = self.magnitudes[k];
            if m < max * 0.012 || m <= self.magnitudes[k - 1] || m < self.magnitudes[k + 1] {
                continue;
            }
            let a = self.magnitudes[k - 1].max(1e-15).ln();
            let b = m.ln();
            let c = self.magnitudes[k + 1].max(1e-15).ln();
            let delta = (0.5 * (a - c) / (a - 2. * b + c)).clamp(-0.5, 0.5);
            let hz = (k as f64 + delta) * self.rate / N as f64;
            if !(55.0..=6000.0).contains(&hz) {
                continue;
            }
            for h in 1..=6 {
                let midi = 69. + 12. * (hz / h as f64 / 440.).log2();
                let note = midi.round();
                if note < LOW as f64
                    || note > (LOW + NOTES - 1) as f64
                    || (midi - note).abs() > 0.35
                {
                    continue;
                }
                let n = note as usize - LOW;
                let score = (m / max).sqrt() / (h as f64).powf(1.3);
                pitches[n] += score;
                if h == 1 {
                    fundamental[n] += score;
                    pitch_sum[n] += midi * score;
                }
            }
        }
        // Require observed fundamental support, avoiding strong subharmonic guesses.
        for n in 0..NOTES {
            pitches[n] *= if fundamental[n] > 0.05 { 1. } else { 0.12 };
        }
        self.output[24..24 + NOTES].copy_from_slice(&fundamental);
        self.output[24 + NOTES..].copy_from_slice(&pitches);
        let mut instant = [0.; 12];
        for n in 0..NOTES {
            if fundamental[n] > 0.05 {
                instant[(n + LOW) % 12] += fundamental[n];
            }
        }
        let total = instant.iter().sum::<f64>().max(1e-12);
        let alpha = 1. - (-dt / 0.18).exp();
        for i in 0..12 {
            self.chroma[i] += alpha * (instant[i] / total - self.chroma[i]);
            self.output[8 + i] = self.chroma[i];
        }
        let energy = self
            .chroma
            .iter()
            .map(|x| x * x)
            .sum::<f64>()
            .sqrt()
            .max(1e-12);
        let mut best = -1.;
        let mut second = -1.;
        let mut chord = (0, 0);
        for root in 0..12 {
            for (quality, intervals) in CHORDS.iter().enumerate() {
                let inside = intervals
                    .iter()
                    .map(|i| self.chroma[(root + i) % 12])
                    .sum::<f64>();
                let weakest = intervals
                    .iter()
                    .map(|i| self.chroma[(root + i) % 12])
                    .fold(f64::INFINITY, f64::min);
                let score = inside / (intervals.len() as f64).sqrt() / energy + weakest * 0.5;
                if score > best {
                    second = best;
                    best = score;
                    chord = (root, quality);
                } else if score > second {
                    second = score;
                }
            }
        }
        let margin = (best - second).max(0.);
        if best > 0.72 && margin > 0.015 {
            self.output[4] = chord.0 as f64;
            self.output[5] = chord.1 as f64;
        }
        self.output[6] = (margin * 10.).clamp(0., 1.);
        // Prefer a continuous, salient upper voice; this is a hypothesis to test,
        // not a claim that the loudest/highest voice is always the melody.
        let mut ranked = [(0., 0usize); 3];
        for n in 12..NOTES {
            let note = (n + LOW) as f64;
            let continuity = if self.previous_pitch > 0. {
                0.12 * (-(note - self.previous_pitch).abs() / 5.).exp()
            } else {
                0.
            };
            let score = pitches[n] * (1. + continuity);
            for i in 0..3 {
                if score > ranked[i].0 {
                    for j in ((i + 1)..3).rev() {
                        ranked[j] = ranked[j - 1];
                    }
                    ranked[i] = (score, n);
                    break;
                }
            }
        }
        let n = ranked[0].1;
        let midi = if fundamental[n] > 0. {
            pitch_sum[n] / fundamental[n]
        } else {
            (n + LOW) as f64
        };
        let confidence = ((ranked[0].0 - ranked[1].0) / ranked[0].0.max(1e-12)).clamp(0., 1.);
        self.output[2] = 440. * 2f64.powf((midi - 69.) / 12.);
        self.output[3] = confidence;
        if ranked[0].0 > 0.15 && confidence > 0.06 {
            self.output[1] = midi;
            self.previous_pitch = midi;
        } else {
            self.previous_pitch = 0.;
        }
        self.output[20] = (ranked[0].1 + LOW) as f64;
        self.output[21] = (ranked[1].1 + LOW) as f64;
        self.output[22] = (ranked[2].1 + LOW) as f64;
        self.output[23] = best;
        1
    }
}

thread_local! {
    static INSTANCE: RefCell<Option<Box<Analyzer>>> = const { RefCell::new(None) };
    static SEQUENCE: RefCell<Vec<f64>> = const { RefCell::new(Vec::new()) };
}
#[no_mangle]
pub extern "C" fn initialize(rate: f64) -> i32 {
    if !rate.is_finite() || !(16000.0..=48000.0).contains(&rate) {
        return -1;
    }
    INSTANCE.with(|a| *a.borrow_mut() = Some(Box::new(Analyzer::new(rate))));
    0
}
#[no_mangle]
pub extern "C" fn input_pointer() -> *mut f32 {
    INSTANCE.with(|a| a.borrow_mut().as_mut().unwrap().input.as_mut_ptr())
}
#[no_mangle]
pub extern "C" fn output_pointer() -> *const f64 {
    INSTANCE.with(|a| a.borrow().as_ref().unwrap().output.as_ptr())
}
#[no_mangle]
pub extern "C" fn analyze(end_frame: f64) -> i32 {
    INSTANCE.with(|a| a.borrow_mut().as_mut().unwrap().run(end_frame))
}
#[no_mangle]
pub extern "C" fn reset() {
    INSTANCE.with(|a| a.borrow_mut().as_mut().unwrap().reset());
}
#[no_mangle]
pub extern "C" fn window_size() -> usize {
    N
}
#[no_mangle]
pub extern "C" fn output_size() -> usize {
    OUT
}

// Isolated second hypothesis: persistent pitches describe accompaniment;
// transient positive residuals describe a moving lead. No instrument labels.
// Fixed maximum controls memory. The host owns neither allocation nor lifetime.
#[no_mangle]
pub extern "C" fn sequence_allocate(frames: usize) -> *mut f64 {
    if frames == 0 || frames > 1500 {
        return std::ptr::null_mut();
    }
    SEQUENCE.with(|s| {
        let mut s = s.borrow_mut();
        *s = vec![0.; frames * OUT];
        s.as_mut_ptr()
    })
}
#[no_mangle]
pub extern "C" fn sequence_refine(lookahead: usize) -> i32 {
    if lookahead > 26 {
        return -1;
    }
    SEQUENCE.with(|s| {
        let mut output = s.borrow_mut();
        let source = output.clone();
        let frames = source.len() / OUT;
        if frames == 0 || source.iter().any(|x| !x.is_finite()) {
            return -1;
        }
        for frame in 0..frames {
            let start = frame.saturating_sub(52 - lookahead);
            let end = (frame + lookahead + 1).min(frames);
            let row = &source[frame * OUT..(frame + 1) * OUT];
            let out = &mut output[frame * OUT..(frame + 1) * OUT];
            let mut persistent = [0.; NOTES];
            let mut values = Vec::with_capacity(end - start);
            for n in 0..NOTES {
                values.clear();
                for f in start..end {
                    values.push(source[f * OUT + 24 + n]);
                }
                values.sort_by(f64::total_cmp);
                persistent[n] = values[values.len() / 2];
            }
            let mut chroma = [0.; 12];
            for n in 0..NOTES {
                chroma[(n + LOW) % 12] += persistent[n];
            }
            let total = chroma.iter().sum::<f64>().max(1e-12);
            for x in &mut chroma {
                *x /= total;
            }
            out[8..20].copy_from_slice(&chroma);
            let energy = chroma.iter().map(|x| x * x).sum::<f64>().sqrt().max(1e-12);
            let mut best = -1.;
            let mut second = -1.;
            let mut chord = (0, 0);
            for root in 0..12 {
                for (quality, intervals) in CHORDS.iter().enumerate() {
                    let inside = intervals
                        .iter()
                        .map(|i| chroma[(root + i) % 12])
                        .sum::<f64>();
                    let weakest = intervals
                        .iter()
                        .map(|i| chroma[(root + i) % 12])
                        .fold(f64::INFINITY, f64::min);
                    let score = inside / (intervals.len() as f64).sqrt() / energy + weakest * 0.5;
                    if score > best {
                        second = best;
                        best = score;
                        chord = (root, quality);
                    } else if score > second {
                        second = score;
                    }
                }
            }
            out[4] = -1.;
            out[5] = -1.;
            out[6] = ((best - second) * 10.).clamp(0., 1.);
            if best > 0.72 && best - second > 0.015 {
                out[4] = chord.0 as f64;
                out[5] = chord.1 as f64;
            }
            let mut candidates = [(0., 0usize); 2];
            for n in 12..NOTES {
                let current = row[24 + n];
                let residual = (current - persistent[n] * 1.1).max(0.);
                let score = row[24 + NOTES + n] * residual / current.max(1e-12);
                if score > candidates[0].0 {
                    candidates[1] = candidates[0];
                    candidates[0] = (score, n);
                } else if score > candidates[1].0 {
                    candidates[1] = (score, n);
                }
            }
            let confidence = (candidates[0].0 - candidates[1].0) / candidates[0].0.max(1e-12);
            out[1] = -1.;
            out[2] = 0.;
            out[3] = confidence;
            if candidates[0].0 > 0.25 && confidence > 0.06 {
                out[1] = (candidates[0].1 + LOW) as f64;
                out[2] = 440. * 2f64.powf((out[1] - 69.) / 12.);
            }
            out[23] = best;
        }
        0
    })
}
