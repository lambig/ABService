/**
 * 外部音源の埋め込みプレイヤーの URL を組み立てる。
 *
 * 音源は自前配信せず外部サービスに委ねる（DECISIONS 7）。埋め込み元のホストはバックエンドの
 * `ExternalAudioUrl` が許可リストで縛るため、ここは受け取った URL を渡すだけでよい。
 * 配信側で frame-src を許可する設定は #240。
 */
export const toEmbedUrl = (audioUrl: string): string =>
  `https://w.soundcloud.com/player/?url=${encodeURIComponent(audioUrl)}&auto_play=false&show_user=true&visual=false`;
