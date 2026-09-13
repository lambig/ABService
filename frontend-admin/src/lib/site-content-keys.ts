/**
 * 公開サイトが読むキー。
 *
 * <p>
 * **候補であって、入れられるキーの一覧ではない。** キーは自由文字列で（#230）、ここに無いキーも入れ
 * られる——閉じると、文言を1つ増やすのにデプロイが要り、自由キーで得たものを失う。持つ理由は逆で、
 * 運営者が公開サイトの実装を読まずに「何を入れる場所か」を知れるようにするため（#383）。
 * </p>
 *
 * <p>
 * 中身はどれも**どこに出るかの説明**で、サイトの内容そのものではない（#230 の線引きの構造側）。
 * 公開サイトがキーを増やしたらここも足す。足し忘れても画面は動き、候補が古くなるだけである。
 * </p>
 */
export const KNOWN_SITE_CONTENT_KEYS = [
  { key: 'site.name', where: 'タイトル・ヘッダーのロゴ・リンクプレビュー' },
  { key: 'site.description', where: 'メタ説明・リンクプレビュー' },
  { key: 'home.introduction', where: 'トップの紹介文' },
  { key: 'site.artist', where: '既定の名義（これと違う名義の作品にだけ名義が出る）' },
  { key: 'footer.copyright.holder', where: 'ページの末尾のコピーライト表示' },
] as const;

/** 候補のうち、まだ登録されていないもの。登録済みは一覧から辿れるため候補に出さない */
export const unregisteredKeys = (
  registered: readonly string[],
): readonly (typeof KNOWN_SITE_CONTENT_KEYS)[number][] =>
  KNOWN_SITE_CONTENT_KEYS.filter((known) => registered.every((key) => key !== known.key));
