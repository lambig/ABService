#!/usr/bin/env node

/**
 * リンクプレビューの既定画像を、サイトの印から焼く（#341）。
 *
 * 印の出所は `public/favicon.svg` ひとつ。既定画像を別の絵として持つと、印を差し替えたときに片方だけが
 * 古くなり、タブとリンクプレビューで違う印が出る。同じ SVG を広い面へ収めて焼くことで、その食い違いが
 * 起こらない形にしている。
 *
 * PNG にするのは、リンクプレビューを読む側が SVG をほぼ受け付けないため。出力は組み立てのたびに作る
 * 生成物で、リポジトリには置かない（`.gitignore`）。
 *
 * 組み立ての前に走る（`package.json` の `prebuild` / `predev`）。
 */

import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';

import sharp from 'sharp';

const publicDir = new URL('../public/', import.meta.url).pathname;

const MARK = join(publicDir, 'favicon.svg');
const OUTPUT = join(publicDir, 'og-default.png');

/** リンクプレビューが想定する大きさ。読む側の多くがこの比で切り出す */
const WIDTH = 1200;
const HEIGHT = 630;

/**
 * 収めたときの余白を塗る色。
 *
 * 印の地と同じ値にする（`favicon.svg` の `--background` 相当）。違う色にすると、正方形の印の周りに
 * 額縁が出る。
 */
const GROUND = '#f8f4ef';

/**
 * 印そのものの大きさ。
 *
 * 面いっぱいにすると印だけの絵になり、リンクプレビューの中で圧が強い。地の余白を広く取って、印を
 * 置いた紙として読めるようにする。
 */
const MARK_SIZE = 360;

/**
 * 印を広い面の中央へ置く。
 *
 * `density` は SVG を読むときの解像度。既定（72dpi）のままだと 32 単位の印が 32px として読まれ、
 * 拡大したときに粗くなる。
 */
const rendered = await sharp(MARK, { density: 1440 })
  .resize({ width: MARK_SIZE, height: MARK_SIZE })
  .extend({
    top: (HEIGHT - MARK_SIZE) / 2,
    bottom: (HEIGHT - MARK_SIZE) / 2,
    left: (WIDTH - MARK_SIZE) / 2,
    right: (WIDTH - MARK_SIZE) / 2,
    background: GROUND,
  })
  .png()
  .toBuffer();

await mkdir(dirname(OUTPUT), { recursive: true });
await writeFile(OUTPUT, rendered);

console.log(
  `リンクプレビューの既定画像を焼きました: ${OUTPUT} (${String(rendered.length)} バイト)`,
);
