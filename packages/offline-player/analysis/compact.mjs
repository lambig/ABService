// SPDX-License-Identifier: MIT
const qualities = ['major','minor','7','maj7','m7','dim','sus4','5','aug','sus2','m7b5','6','m6'];
const encode = bytes => {
  const parts=[];
  // Multiples of three keep padding confined to the last chunk.
  for(let i=0;i<bytes.length;i+=12288) parts.push(btoa(String.fromCharCode(...bytes.subarray(i,i+12288))));
  return parts.join('');
};
/** Fixed-grid melody retains every voiced bin/float32 salience; harmony retains float64 boundaries. */
export function compactTimeline(duration) {
  if(!Number.isFinite(duration)||duration<=0||duration>14400)throw Error('Invalid analysis duration');
  const count=Math.ceil(duration*22050/256),bytes=new Uint8Array(count*6),view=new DataView(bytes.buffer);
  let next=0;
  return {
    push(frame,bin,salience) {
      if(frame!==next||frame>=count||!Number.isInteger(bin)||!(bin===-1||(bin>=0&&bin<360))||!Number.isFinite(salience)||salience<0||salience>1)throw Error('Invalid melody frame');
      view.setUint16(frame*6,bin===-1?65535:bin,true);
      view.setFloat32(frame*6+2,bin===-1?0:salience,true);
      next++;
    },
    finish(harmony) {
      if(next!==count)throw Error('Incomplete melody timeline');
      if(harmony.length>200000)throw Error('Harmony event limit');
      const chords=new Uint8Array(harmony.length*19),data=new DataView(chords.buffer);
      harmony.forEach((row,i)=>{
        const quality=qualities.indexOf(row.quality),offset=i*19;
        if(!Number.isFinite(row.start)||row.start<0||row.end<=row.start||row.end>duration||!Number.isFinite(row.end)||
          (i&&row.start<harmony[i-1].end)||!Number.isInteger(row.root)||row.root<0||row.root>11||quality<0||
          !(row.bass===null||(Number.isInteger(row.bass)&&row.bass>=0&&row.bass<=11)))throw Error('Invalid harmony interval');
        data.setFloat64(offset,row.start,true);data.setFloat64(offset+8,row.end,true);
        data.setUint8(offset+16,row.root);data.setUint8(offset+17,quality);data.setUint8(offset+18,row.bass===null?255:row.bass);
      });
      return {melody:encode(bytes),harmony:encode(chords)};
    },
  };
}
