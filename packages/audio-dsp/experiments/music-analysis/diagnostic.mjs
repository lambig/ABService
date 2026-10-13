import {frameAt, noteLabel, chordLabel} from './diagnostic-clock.mjs';
const $ = id => document.getElementById(id);
const audio = $('audio'), plot = $('plot'), ctx = plot.getContext('2d');
let data = null, generation = 0;
audio.volume = .2;

function draw(time) {
  const rows = data.modes[$('mode').value].rows, key = $('clock').value;
  const w = plot.clientWidth, h = plot.clientHeight, left = 35, bottom = h - 25;
  const scale = window.devicePixelRatio || 1;
  plot.width = Math.round(w * scale); plot.height = Math.round(h * scale);
  ctx.setTransform(scale, 0, 0, scale, 0, 0);
  const x = t => left + t / data.duration * (w - left - 10);
  const y = n => bottom - (n - 36) / 60 * (bottom - 12);
  ctx.clearRect(0, 0, w, h); ctx.font = '13px system-ui';
  ctx.lineWidth = 1;
  for (let n = 36; n <= 96; n += 12) {
    ctx.strokeStyle = '#33425b'; ctx.beginPath(); ctx.moveTo(left, y(n)); ctx.lineTo(w, y(n)); ctx.stroke();
    ctx.fillStyle = '#c1cede'; ctx.fillText(noteLabel(n), 3, y(n) + 4);
  }
  for (let t = 0; t <= data.duration; t += 2) {
    ctx.textAlign = t === data.duration ? 'right' : 'left';
    ctx.fillText(`${t}s`, x(t), h - 5);
  }
  ctx.textAlign = 'left';
  for (const [series, color, field, span] of [[data.truth ?? [], '#67e8ae', 'time', .01], [rows, '#ffbe75', key, data.hopSeconds]]) {
    ctx.strokeStyle = color; ctx.lineWidth = 3; ctx.beginPath();
    for (const r of series) if (r.note >= 0 && r[field] < data.duration) {
      ctx.moveTo(x(r[field]), y(r.note)); ctx.lineTo(x(Math.min(data.duration, r[field] + span)), y(r.note));
    }
    ctx.stroke();
  }
  ctx.strokeStyle = '#fff'; ctx.lineWidth = 1; ctx.beginPath(); ctx.moveTo(x(time), 0); ctx.lineTo(x(time), bottom); ctx.stroke();
}

function render() {
  if (!data) return;
  const time = audio.currentTime, key = $('clock').value;
  const result = data.modes[$('mode').value];
  const row = frameAt(result.rows, time, key, data.hopSeconds + 1e-7);
  const truth = data.truth ? frameAt(data.truth, time, 'time', .0100001) : null;
  $('time').value = time.toFixed(2);
  $('chord').textContent = chordLabel(row);
  $('note').textContent = noteLabel(row?.note);
  $('frame').textContent = row ? `対象 ${row.time.toFixed(3)} s / 取得可能 ${row.availableAt.toFixed(3)} s` : '分析対象外';
  $('truth-chord').textContent = data.truth ? `正解：${chordLabel(truth)}` : '正解データなし：精度は未判定';
  $('truth-note').textContent = data.truth ? `正解：${noteLabel(truth?.note)}` : '正解データなし：精度は未判定';
  $('latency').textContent = `必要な入力待ち：窓の後半 ${result.timing.lookbackLatencyMs.toFixed(0)} ms ＋ 先読み ${result.timing.extraLookaheadMs.toFixed(0)} ms。表示差と音符の推定誤差は別に確認します。`;
  // Observable state for synchronization regression tests, taken from the same
  // frame used for the labels and plot; no separate test-only playback clock.
  plot.dataset.time = String(time);
  plot.dataset.frameTime = row ? String(row.time) : '';
  plot.dataset.truthNote = truth ? String(truth.note) : '';
  plot.dataset.note = row ? String(row.note) : '';
  draw(time);
}
async function load() {
  const request = ++generation, id = $('case').value;
  audio.pause(); data = null;
  audio.removeAttribute('src'); audio.load();
  $('error').textContent = ''; $('status').textContent = '読み込み中';
  for (const id of ['chord', 'note', 'frame', 'truth-chord', 'truth-note']) $(id).textContent = '—';
  $('time').value = '0.00'; ctx.clearRect(0, 0, plot.width, plot.height);
  try {
    const response = await fetch(id + '.json'); if (!response.ok) throw Error('解析結果の取得に失敗しました');
    const next = await response.json(); if (request !== generation) return;
    data = next; audio.src = next.audio; audio.load();
    $('status').textContent = `${next.label} · ${next.duration.toFixed(1)} s · ${next.truth ? '合成音・正解あり' : '実音源・正解の照合待ち'}`;
    render();
  } catch (error) {
    if (request === generation) { $('error').textContent = error.message; $('status').textContent = '読み込み失敗'; }
  }
}
for (const event of ['timeupdate', 'seeking', 'seeked', 'pause', 'ended', 'loadedmetadata']) audio.addEventListener(event, render);
audio.addEventListener('error', () => { if (audio.getAttribute('src')) $('error').textContent = '音源を読み込めませんでした'; });
$('mode').addEventListener('change', render); $('clock').addEventListener('change', render);
$('case').addEventListener('change', load);
window.addEventListener('resize', render);
function tick() { if (!audio.paused) render(); requestAnimationFrame(tick); }
requestAnimationFrame(tick);
try {
  const response = await fetch('index.json'); if (!response.ok) throw Error('音源一覧を取得できませんでした');
  const index = await response.json();
  for (const c of index.cases) $('case').add(new Option(c.label, c.id));
  $('case').disabled = false; await load();
} catch (error) { $('error').textContent = error.message; }
