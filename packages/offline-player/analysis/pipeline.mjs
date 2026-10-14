// SPDX-License-Identifier: GPL-3.0-or-later
import {blobSource, openFlac, harmonyInput, melodyInput} from './input.mjs';
import {analyzeStream} from './stream-loader.mjs';
import {createOpfsStore} from './path-store.mjs';

const pause = () => new Promise(resolve => setTimeout(resolve, 0));
const qualities = {'':'major',m:'minor','7':'7',maj7:'maj7',m7:'m7',dim:'dim',sus4:'sus4','5':'5',aug:'aug',sus2:'sus2',m7b5:'m7b5','6':'6',m6:'m6'};
const pitchClass = name => (({C:0,D:2,E:4,F:5,G:7,A:9,B:11})[name[0]] + (name[1] === '#' ? 1 : name[1] === 'b' ? -1 : 0) + 12) % 12;
export function harmonyRows(events, duration) {
  if (!events.length) throw Error('Missing harmony timeline');
  return events.flatMap(({time, label}, i) => {
    const end = events[i + 1]?.time ?? duration;
    if (!Number.isFinite(time) || time < 0 || end < time || end > duration) throw Error('Invalid harmony timeline');
    if (label === 'N') return [];
    const match = /^([A-G][b#]?)([^/]*)(?:\/([A-G][b#]?))?$/.exec(label);
    if (!match || !Object.hasOwn(qualities, match[2])) throw Error('Unsupported chord');
    return time === end ? [] : [{start:time,end,root:pitchClass(match[1]),quality:qualities[match[2]],bass:match[3] ? pitchClass(match[3]) : null}];
  });
}

/** Bounded windows and tiles preserve the accepted PC grid and gate across windows. */
export async function analyze(blob, modules, {signal, progress, engines, analysisVersion}) {
  const source = blobSource(blob);
  const header = await openFlac(modules.flac, source, {signal});
  const {samples, rate} = header.metadata;
  header.close();
  const duration = samples / rate;
  const result = await analyzeStream(modules.harmony, {
    samples:Math.ceil(duration * 22050), signal,
    inputFactory:() => harmonyInput(modules.flac, modules.soxr, source, {signal}),
    storeFactory:createOpfsStore,
    progress:({pass, samples, total}) => progress({phase:'harmony',pass,completed:samples,total}),
  });
  const harmony = harmonyRows(result.events, duration);
  const hcqt = (await WebAssembly.instantiate(modules.hcqt)).instance.exports;
  const cnn = (await WebAssembly.instantiate(modules.melody)).instance.exports;
  const melody = [];
  let active = -1;
  try {
    for await (const window of melodyInput(modules.flac, modules.soxr, source, {signal})) {
      signal.throwIfAborted();
      const pointer = hcqt.initialize(window.pcm.length);
      if (!pointer) throw Error('Invalid melody window');
      new Float32Array(hcqt.memory.buffer, pointer, window.pcm.length).set(window.pcm);
      const output = hcqt.infer();
      const frames = Math.floor(window.pcm.length / 256) + 1;
      const feature = new Float32Array(hcqt.memory.buffer, output, 360 * frames * 6);
      for (let start = window.start; start < window.end; start += 32) {
        await pause(); signal.throwIfAborted();
        const end = Math.min(window.end, start + 32);
        const left = Math.max(0, start - window.left - 7);
        const right = Math.min(frames, end - window.left + 7), count = right - left;
        const input = cnn.initialize(count);
        if (!input) throw Error('Invalid melody tile');
        const tensor = new Float32Array(cnn.memory.buffer, input, 360 * count * 6);
        for (let bin = 0; bin < 360; bin++) tensor.set(feature.subarray((bin*frames+left)*6,(bin*frames+right)*6),bin*count*6);
        const inferred = cnn.infer();
        const saliences = new Float32Array(cnn.memory.buffer, inferred, 360*count);
        for (let frame = start; frame < end; frame++) {
          const local = frame-window.left-left;
          if (local < 0 || local >= count) throw Error('Incomplete melody grid');
          let bin = 0, salience = saliences[local];
          for (let k = 1; k < 360; k++) if (saliences[k*count+local] > salience) {bin=k; salience=saliences[k*count+local];}
          const midi = 69+12*Math.log2(32.7/440)+bin/5;
          const threshold = active >= 0 && Math.abs(midi-active) <= .5 ? .2 : .35;
          active = salience >= threshold ? midi : -1;
          if (active >= 0) melody.push({start:frame*256/22050,end:Math.min(duration,(frame+1)*256/22050),midi,salience});
          if (melody.length + harmony.length > 200000) throw Error('Analysis event limit');
        }
        cnn.dispose();
        progress({phase:'melody',completed:end,total:Math.ceil(duration*22050/256)});
      }
      hcqt.dispose();
    }
    signal.throwIfAborted();
    return {schemaVersion:1,durationSeconds:duration,engines,analysisVersion,melody,harmony};
  } finally {hcqt.dispose();cnn.dispose();}
}
