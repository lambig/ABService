// Select from the media clock on every render: pause, rewind and mode changes
// need no independent timer or retained analysis cursor.
export function frameAt(rows, time, key = 'time', maxAge = Infinity) {
  let lo = 0, hi = rows.length;
  while (lo < hi) {
    const mid = (lo + hi) >>> 1;
    if (rows[mid][key] <= time) lo = mid + 1; else hi = mid;
  }
  const row = rows[lo - 1];
  return row && time - row[key] < maxAge ? row : null;
}

export function noteLabel(note) {
  if (!(note >= 0)) return '—';
  const rounded = Math.round(note);
  return `${['C','C♯','D','E♭','E','F','F♯','G','A♭','A','B♭','B'][rounded % 12]}${Math.floor(rounded / 12) - 1}`;
}
export function chordLabel(row) {
  if (!row || row.root < 0 || row.quality < 0) return '—';
  return ['C','C♯','D','E♭','E','F','F♯','G','A♭','A','B♭','B'][row.root]
    + [' major',' minor',' 7',' maj7',' min7',' dim',' sus4'][row.quality];
}
