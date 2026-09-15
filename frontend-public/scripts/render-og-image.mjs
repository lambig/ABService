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
import { fileURLToPath } from 'node:url';

import sharp from 'sharp';

const publicDir = fileURLToPath(new URL('../public/', import.meta.url));

const MARK = join(publicDir, 'favicon.svg');
const OUTPUT = join(publicDir, 'og-default.png');

/** リンクプレビューが想定する大きさ。読む側の多くがこの比で切り出す */
const WIDTH = 1200;
const HEIGHT = 630;

/**
 * 印そのものの大きさ。
 *
 * 面いっぱいにすると印だけの絵になり、リンクプレビューの中で圧が強い。地の余白を広く取って、印を
 * 置いた紙として読めるようにする。
 */
const MARK_SIZE = 360;

/**
 * SVG を読むときの解像度。
 *
 * 既定（72dpi）のままだと 32 単位の印が 32px として読まれ、拡大したときに粗くなる。
 */
const DENSITY = 1440;

const markOf = () =>
  sharp(MARK, { density: DENSITY }).resize({ width: MARK_SIZE, height: MARK_SIZE });

/**
 * 余白を塗る色を、印そのものから取る。
 *
 * <p>
 * **ここで色を知らないことが、差し替えが1ファイルで済む条件である。** 同じ値を焼く側にも持つと、印を
 * 差し替えて地の色が変わったとき、印の外側だけが旧い色のまま残って額縁になる。落ちないので、リンク
 * プレビューを誰かが見るまで分からない。
 * </p>
 *
 * <p>
 * 左上の画素を見る。印が地を塗る限り、そこは地である（塗らない印なら透過が返り、余白も透過になる
 * ——その場合の地は印の持ち主が決めたものになる）。
 * </p>
 */
const groundOf = async () => {
  const { data } = await markOf().ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  const [red, green, blue, alpha] = data;

  return { r: red, g: green, b: blue, alpha: alpha / 255 };
};

/** 印を広い面の中央へ置く */
const rendered = await markOf()
  .extend({
    top: (HEIGHT - MARK_SIZE) / 2,
    bottom: (HEIGHT - MARK_SIZE) / 2,
    left: (WIDTH - MARK_SIZE) / 2,
    right: (WIDTH - MARK_SIZE) / 2,
    background: await groundOf(),
  })
  .png()
  .toBuffer();

await mkdir(dirname(OUTPUT), { recursive: true });
await writeFile(OUTPUT, rendered);

console.log(
  `リンクプレビューの既定画像を焼きました: ${OUTPUT} (${String(rendered.length)} バイト)`,
);
