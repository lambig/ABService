import type { AdminArticleDetail, ArticleFields } from './client';

/**
 * 入力欄の位置。
 *
 * <p>
 * 綴りは**管理APIが検証エラーの `field` として返す入力パス**に揃える（`ArticleInputPaths` と各値
 * オブジェクトが決める）。画面の内部名を別に持つと、応答の位置から欄を引くための対応表が要り、検証を
 * 1つ足すたびに2箇所を変えることになる（DECISIONS 29）。
 * </p>
 */
export const ARTICLE_FIELD_PATHS = [
  'articleType',
  'title',
  'body',
  'bodyFormat',
  'introShort',
] as const;

/** 入力欄の位置 */
export type ArticleFieldPath = (typeof ARTICLE_FIELD_PATHS)[number];

/**
 * 編集中の入力値。
 *
 * <p>
 * 未入力は空文字で持ち、`null` と `undefined` を混ぜない。どちらも「入力されていない」を表せてしまうと、
 * 欄の値を読む側が両方を見る必要が出る。API へ渡す段で空文字を未指定（項目を送らない）へ写す。
 * </p>
 */
export type ArticleDraft = Readonly<Record<ArticleFieldPath, string>>;

/**
 * 記事の種別。
 *
 * <p>
 * `ALBUM` だけが作品への参照を持てる（DECISIONS 21）。参照そのものを扱うのは別スライスで、ここでは
 * 種別を選べるところまでを持つ。
 * </p>
 */
export const ARTICLE_TYPES = ['ALBUM', 'NOTE', 'NEWS', 'EVENT', 'OTHER'] as const;

/** 本文のマークアップ形式。`body` を指定するときだけ意味を持つ */
export const BODY_FORMATS = ['PLAIN_TEXT', 'MARKDOWN'] as const;

/** 新規作成の初期値。選択肢を持つ欄だけは既定を持つ（どれでもない状態を作らない） */
export const EMPTY_DRAFT: ArticleDraft = {
  articleType: 'NOTE',
  title: '',
  body: '',
  bodyFormat: 'PLAIN_TEXT',
  introShort: '',
};

/** 既存の記事を編集の初期値へ写す。 */
export const draftOf = (article: AdminArticleDetail): ArticleDraft => ({
  articleType: article.articleType,
  title: article.title,
  body: article.body,
  bodyFormat: article.bodyFormat,
  introShort: article.introShort,
});

/** 1つの欄だけを差し替えた入力値を返す */
export const withValue = (
  draft: ArticleDraft,
  path: ArticleFieldPath,
  value: string,
): ArticleDraft => ({
  ...draft,
  [path]: value,
});

/**
 * 入力された値。空白だけなら未指定として扱う。
 *
 * <p>
 * **判定にだけ空白を落とし、送る値は加工しない。** 更新は全項目置換で、画面が正規化した値がそのまま
 * 保存される。本文とショート紹介文は前後の空白も文章の一部であり、バックエンドの {@code MarkupContent}
 * は受け取った本文を加工せず保持する契約である。ここで整えると、別の項目を変えただけの保存が、触って
 * いない項目の値を黙って書き換える。
 * </p>
 */
const presence = (value: string): string | undefined => (value.trim() === '' ? undefined : value);

/**
 * 入力値を、作成・更新の要求へ写す。
 *
 * <p>
 * 空文字は項目そのものを送らない形へ落とす。必須の判定は行わない——必須かどうかはバックエンドの
 * 検証が持ち、画面が同じ規則を持つと2箇所へ散る。
 * </p>
 */
export const articleFieldsOf = (draft: ArticleDraft): ArticleFields => ({
  articleType: presence(draft.articleType),
  title: presence(draft.title),
  body: presence(draft.body),
  bodyFormat: presence(draft.bodyFormat),
  introShort: presence(draft.introShort),
});
