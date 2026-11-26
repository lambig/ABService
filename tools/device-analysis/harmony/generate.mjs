import fs from 'node:fs';
const root=new URL('.',import.meta.url);
const source=fs.readFileSync(new URL('upstream/chordino/Chordino.cpp',root),'utf8');
function between(start,end){const a=source.indexOf(start),b=source.indexOf(end,a);if(a<0||b<a||source.indexOf(start,a+1)>=0)throw Error('Ambiguous source extraction '+start);return source.slice(a,b);}
const tuning=between('    float meanTuningImag = 0;','    char buffer0');
let frame=between('        Feature currentLogSpectrum = *i;','        tunedSpec.push_back(currentTunedSpec);');
frame=frame.replace('Feature currentLogSpectrum = *i;','Feature currentLogSpectrum = m_logSpectrum.front();').replace('        timestamps.push_back(currentLogSpectrum.timestamp);','');
const localStart=frame.indexOf('        if (m_tuneLocal)'),localEnd=frame.indexOf('\n        }',localStart);if(localStart<0||localEnd<0)throw Error('Local tuning block');frame=frame.slice(0,localStart)+frame.slice(localEnd+10);
let chroma=between('        Feature currentTunedSpec = *it;','        chordogram.push_back(currentChordSalience);');
chroma=chroma.replace('        Feature currentTunedSpec = *it; // logfreq spectrum','').replace('        chromaList.push_back(currentChromas);','');
const template=fs.readFileSync(new URL('stream-core.template.cpp',root),'utf8');
fs.writeFileSync(new URL('stream-core.cpp',root),template.replace('/* TUNING */',tuning).replace('/* FRAME */',frame).replace('/* CHROMA */',chroma).replace(/[ \t]+$/gm,''));
console.log('Generated frame code from fixed Chordino source; global tuning selected');
