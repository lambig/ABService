/**
 * カバー画像として選ぶ実体。
 *
 * <p>
 * 画像ファイルをリポジトリへ置かず、ここで組み立てる。置いた場合、何が写っているのかは開くまで
 * 分からず、なぜその形式・その大きさなのかも残らない。
 * </p>
 */

/**
 * 96×96 の単色（`#C2410C`）PNG、199バイト。
 *
 * <p>
 * 単色にするのは、証跡でカバー画像の区画がひと目で分かるようにするため。バックエンドは申告された
 * 形式を信用せず先頭バイト列で形式を判定するため、**PNG の署名を持つ本物の実体**である必要がある。
 * </p>
 */
const SOLID_PNG_BASE64 =
  'iVBORw0KGgoAAAANSUhEUgAAAGAAAABgCAIAAABt+uBvAAAAjklEQVR42u3QMQ0AAAgDsAnAFNZRhgNOriZV0EwXhygQJEiQIEGCBAlCkCBBggQJEiQIQYIECRIkSJAgBAkSJEiQIEGCBCFIkCBBggQJEoQgQYIECRIkSBCCBAkSJEiQIEGCECRIkCBBggQJQpAgQYIECRIkCEGCBAkSJEiQIEEIEiRIkCBBggQhSJCgPwtMwB47NN8y0AAAAABJRU5ErkJggg==';

/**
 * 受け入れられる画像。選ぶと、払い出し・送信・確定の3段すべてを通る。
 *
 * <p>
 * `width` は描かれたかどうかを見るために持つ。`src` が入っただけの状態（配信が取り次いでいない、
 * 実体が届いていない）と区別するには、ブラウザが読み込んだ実寸を確かめるほかない。
 * </p>
 */
export const acceptedCoverImage = {
  name: 'e2e-cover.png',
  mimeType: 'image/png',
  buffer: Buffer.from(SOLID_PNG_BASE64, 'base64'),
  width: 96,
} as const;

/**
 * 組み立ての前に管理API経由で入れるときの形。
 *
 * <p>
 * 画面から選ぶときはファイルとして渡すため名前が要り、シードは実体と申告だけでよい。**実体は
 * {@link acceptedCoverImage} と同じものを使う**——別に持つと、片方を差し替えたときに画面から入れた
 * 画像とシードした画像が食い違い、証跡の見た目だけが理由なく変わる。
 * </p>
 *
 * <p>
 * 単色のため、どの大きさで描かれても見た目は変わらない。一覧のカード（実寸）と作品の詳細（拡大）で
 * 同じ絵が出る。
 * </p>
 */
export const coverImageAsset = {
  contentType: acceptedCoverImage.mimeType,
  body: new Blob([acceptedCoverImage.buffer], { type: acceptedCoverImage.mimeType }),
  width: acceptedCoverImage.width,
} as const;

/**
 * 形式そのものが受け入れられない画像。
 *
 * <p>
 * GIF は受け入れる形式に無いため、**払い出しの段で断られる**（送信も確定も起きない）。ファイルを
 * 選ぶ窓は `image/*` で絞っており、この形式も選べてしまう——断り方を見る必要があるのはそのため。
 * </p>
 */
export const unsupportedCoverImage = {
  name: 'e2e-cover.gif',
  mimeType: 'image/gif',
  buffer: Buffer.from('GIF89a', 'ascii'),
} as const;

/**
 * 保管先へは送れるが、確定に通らない実体。
 *
 * <p>
 * 申告は PNG で、中身は PNG ではない。バックエンドは申告を信用せず先頭バイト列で形式を判定するため、
 * 払い出し（申告だけを見る）も保管先への送信も通り、**確定の検査で初めて落ちる**。
 * </p>
 *
 * <p>
 * 3段のうち最後だけが拒む唯一の経路である。「送れた実体でも、確定に通らなければその鍵を入力へ
 * 入れない」という契約は、形式そのものが弾かれる {@link unsupportedCoverImage} では踏めない。
 * </p>
 */
export const unconfirmableCoverImage = {
  name: 'e2e-cover-mismatch.png',
  mimeType: 'image/png',
  buffer: Buffer.from('this is not a PNG', 'ascii'),
} as const;
