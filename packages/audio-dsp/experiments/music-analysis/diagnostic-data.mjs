import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {analyze, fixture, rate, hop} from './evaluate.mjs';

const dir = path.dirname(fileURLToPath(import.meta.url));
export const names = ['chords', 'sevenths', 'melody', 'accompanied-melody',
  'loud-accompaniment', 'silence', 'sustained-lead', 'moving-accompaniment'];

// IEEE float WAV preserves the same samples used by the analysis, including
// over-range synthetic samples. Playback uses a fixed gain; analysis is unscaled.
export function wav(pcm, sampleRate) {
  const out = Buffer.alloc(44 + pcm.length * 4);
  out.write('RIFF'); out.writeUInt32LE(out.length - 8, 4); out.write('WAVEfmt ', 8);
  out.writeUInt32LE(16, 16); out.writeUInt16LE(3, 20); out.writeUInt16LE(1, 22);
  out.writeUInt32LE(sampleRate, 24); out.writeUInt32LE(sampleRate * 4, 28);
  out.writeUInt16LE(4, 32); out.writeUInt16LE(32, 34); out.write('data', 36);
  out.writeUInt32LE(pcm.length * 4, 40);
  for (let i = 0; i < pcm.length; i++) out.writeFloatLE(pcm[i], 44 + i * 4);
  return out;
}

export async function writeCase(out, id, pcm, sampleRate, truth = null, label = id) {
  if (!/^[a-z0-9-]+$/.test(id)) throw Error('Invalid case id');
  if (!Number.isInteger(sampleRate) || sampleRate < 16000 || sampleRate > 48000
      || pcm.length < 8192 || pcm.length > sampleRate * 16
      || !pcm.every(Number.isFinite)) throw Error('Expected finite mono PCM, 16000–48000 Hz, <=16 seconds');
  const modes = {};
  for (const mode of ['baseline', 'causal', 'lookahead']) modes[mode] = await analyze(pcm, sampleRate, mode);
  const duration = pcm.length / sampleRate;
  const expected = truth ? Array.from({length: Math.ceil(duration * 100)}, (_, i) => ({time: i / 100, ...truth(i / 100)})) : null;
  const audio = wav(pcm, sampleRate);
  const data = {id, label, sampleRate, duration, hopSeconds: hop / sampleRate,
    audio: id + '.wav', audioSha256: createHash('sha256').update(audio).digest('hex'),
    kernelSha256: createHash('sha256').update(fs.readFileSync(path.join(dir, 'kernel.wasm'))).digest('hex'),
    truth: expected, modes};
  fs.writeFileSync(path.join(out, id + '.wav'), audio);
  fs.writeFileSync(path.join(out, id + '.json'), JSON.stringify(data));
  return {id, label};
}

export function writeViewer(out, cases) {
  for (const f of ['diagnostic.html', 'diagnostic.mjs', 'diagnostic-clock.mjs']) fs.copyFileSync(path.join(dir, f), path.join(out, f));
  fs.writeFileSync(path.join(out, 'index.json'), JSON.stringify({cases}));
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const out = path.resolve(process.argv[2] ?? path.join(dir, 'results', 'diagnostic'));
  fs.mkdirSync(out, {recursive: true});
  const cases = [];
  for (const name of names) {
    const f = fixture(name);
    cases.push(await writeCase(out, name, f.pcm, rate, f.truth));
  }
  writeViewer(out, cases);
  console.log(`Generated ${cases.length} diagnostic cases: ${out}`);
}
