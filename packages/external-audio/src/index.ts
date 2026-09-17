/**
 * 公開サイトと管理画面で使う SoundCloud プレイヤーの URL を組み立てる。
 *
 * 入力は単一のクエリ値へ符号化し、埋め込み先は常に同じ HTTPS オリジンとする。
 * 未保存の入力も受け取るが、保存の可否や音源の存在は判定しない。保存の検証は backend が担う。
 * 配信側の CSP は両画面の frame-src に https://w.soundcloud.com を許可する（#240）。
 * visual はアートワークを背景に敷く表示。プレイヤーの絵を作品の顔として使うため、こちらを採る（#415）。
 */
export const toEmbedUrl = (audioUrl: string): string =>
  `https://w.soundcloud.com/player/?url=${encodeURIComponent(audioUrl)}&auto_play=false&show_user=true&visual=true`;
