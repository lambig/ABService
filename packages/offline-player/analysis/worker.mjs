// SPDX-License-Identifier: GPL-3.0-or-later
import {analyze} from './pipeline.mjs';

self.onmessage = async ({data}) => {
  const controller = new AbortController();
  self.onmessage = () => controller.abort();
  try {
    const load = async path => {
      const response = await fetch(new URL(path, import.meta.url));
      if (!response.ok) throw Error('Analysis module unavailable');
      return response;
    };
    const {engines, analysisVersion} = await (await load('identity.json')).json();
    const modules = Object.fromEntries(await Promise.all(
      [['flac','flac.wasm'],['soxr','soxr.wasm'],['harmony','stream-harmony.wasm'],['hcqt','hcqt.wasm'],['melody','melody.wasm']]
        .map(async ([key,path]) => [key,await (await load(path)).arrayBuffer()]),
    ));
    const result = await analyze(data.blob, modules, {
      signal:controller.signal, engines, analysisVersion,
      progress:value => self.postMessage({kind:'progress',...value}),
    });
    self.postMessage({kind:'result',value:{...result,audioSha256:data.audioSha256}});
  } catch (error) {
    self.postMessage({kind:'error',name:error.name,message:error.message});
  }
};
