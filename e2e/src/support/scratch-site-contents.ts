import { workerKeySegment } from './worker.ts';

/**
 * 検査のためだけに使うサイト文言のキー。公開サイトはこれを読まない。
 *
 * <p>
 * 文言には削除の経路が無い（バックエンドも持たない）ため、実行ごとに新しいキーを作ると、ローカルの
 * データベースに消せないものが積み上がる。キーは worker ごとに1つを使い回し、保存されたことは本文へ
 * 実行ごとの印を入れて確かめる。worker ごとに分けるのは、同じキーへ別の worker が書くと互いの本文の
 * 検査が崩れるため（#430）。積み上がる数は worker 数で頭打ちになる。
 * </p>
 *
 * <p>
 * 形式はバックエンドの `SiteContentKey` が受け付けるもの（小文字の英数字の区切りをドットで繋ぐ）。
 * </p>
 */
export const scratchSiteContentKey = (): string => `e2e.scratch.${workerKeySegment()}.text`;
