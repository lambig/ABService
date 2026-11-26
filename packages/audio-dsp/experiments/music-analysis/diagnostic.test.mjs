import assert from 'node:assert/strict';
import {test} from 'node:test';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';
import {frameAt} from './diagnostic-clock.mjs';
import {fixture, rate} from './evaluate.mjs';
import {writeCase, writeViewer} from './diagnostic-data.mjs';
import {serve} from './diagnostic-server.mjs';

test('frame selection covers start, rewind, gaps, tail and input availability', () => {
  const rows = [{time: .2, availableAt: .4}, {time: .3, availableAt: .5}];
  assert.equal(frameAt(rows, .1, 'time', .1), null);
  assert.equal(frameAt(rows, .25, 'time', .1), rows[0]);
  assert.equal(frameAt(rows, .35, 'time', .1), rows[1]);
  assert.equal(frameAt(rows, .25, 'time', .1), rows[0]);
  assert.equal(frameAt(rows, .45, 'time', .1), null);
  assert.equal(frameAt(rows, .35, 'availableAt', .1), null);
  assert.equal(frameAt(rows, .45, 'availableAt', .1), rows[0]);
});

test('browser follows media time through playback, pause, seek and comparison changes', async t => {
  const dir = path.dirname(fileURLToPath(import.meta.url));
  const output = path.join(dir, 'results', 'diagnostic-test');
  fs.mkdirSync(output, {recursive:true});
  const cases = [];
  for (const name of ['melody', 'chords']) {
    const f = fixture(name);
    cases.push(await writeCase(output, name, f.pcm, rate, f.truth));
  }
  writeViewer(output, cases);
  const {server, url} = await serve(output);
  t.after(() => new Promise(resolve => server.close(resolve)));
  const response = await fetch(new URL('/index.json', url));
  assert.equal(response.status, 404);
  assert.equal((await fetch(url + '../index.json')).status, 404);
  const browser = await chromium.launch({executablePath: process.env.DIAGNOSTIC_CHROMIUM || undefined});
  t.after(() => browser.close());
  const page = await browser.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto(url);
  await page.waitForFunction(() => document.querySelector('audio').readyState >= 1);
  const snapshot = () => page.evaluate(() => ({
    media: document.querySelector('audio').currentTime,
    frame: document.querySelector('#plot').dataset.frameTime,
    displayed: Number(document.querySelector('#plot').dataset.time),
    truth: document.querySelector('#plot').dataset.truthNote,
  }));
  async function seek(time) {
    await page.evaluate(time => {document.querySelector('audio').currentTime = time;}, time);
    await page.waitForFunction(time => Math.abs(Number(document.querySelector('#plot').dataset.time) - time) < .02, time);
    return snapshot();
  }
  assert.equal((await seek(0)).frame, '');
  assert.equal((await seek(2.7)).truth, '76');
  const previous = await snapshot();
  await page.selectOption('#mode', 'lookahead');
  assert.equal((await snapshot()).media, previous.media);
  await page.selectOption('#clock', 'availableAt');
  const delayed = await snapshot();
  assert.ok(delayed.media - Number(delayed.frame) > .78);
  assert.equal((await seek(.1)).frame, '');
  await page.selectOption('#clock', 'time');
  assert.equal((await seek(1.2)).truth, '76');
  await page.evaluate(async () => { const audio = document.querySelector('audio'); audio.muted = true; await audio.play(); });
  await page.waitForFunction(() => document.querySelector('audio').currentTime > 1.6);
  let playing = await snapshot();
  assert.ok(Math.abs(playing.media - playing.displayed) < .12);
  await page.evaluate(() => document.querySelector('audio').pause());
  const paused = await snapshot();
  await page.waitForTimeout(150);
  assert.equal((await snapshot()).media, paused.media);
  assert.equal((await seek(15.99)).frame, '');
  await seek(15.8);
  await page.evaluate(() => { const audio = document.querySelector('audio'); audio.loop = true; return audio.play(); });
  await page.waitForFunction(() => document.querySelector('audio').currentTime < .15);
  assert.ok((await snapshot()).displayed < .2);
  await page.evaluate(() => document.querySelector('audio').pause());
  // A slower response for an older selection must not overwrite the latest one.
  await page.route('**/chords.json', async route => { await new Promise(r => setTimeout(r, 200)); await route.continue(); });
  await page.selectOption('#case', 'chords');
  await page.selectOption('#case', 'melody');
  await page.waitForFunction(() => document.querySelector('#status').textContent.startsWith('melody ·'));
  await page.waitForTimeout(300);
  assert.match(await page.locator('#status').textContent(), /^melody ·/);
  assert.ok((await page.locator('audio').getAttribute('src')).endsWith('melody.wav'));
  for (const viewport of [{width:412,height:915}, {width:915,height:412}]) {
    await page.setViewportSize(viewport);
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
  }
  assert.deepEqual(errors, []);
  assert.equal(await page.locator('#error').textContent(), '');
});
