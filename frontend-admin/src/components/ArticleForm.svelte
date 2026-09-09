<script lang="ts">
  import ApiKeyForm from '$components/ApiKeyForm.svelte';
  import ArticleAlbumEditor from '$components/ArticleAlbumEditor.svelte';
  import ArticleTagsEditor from '$components/ArticleTagsEditor.svelte';
  import ConfirmDialog from '$components/ConfirmDialog.svelte';
  import { Button } from '$components/ui/button/index.js';
  import {
    ARTICLE_TYPES,
    BODY_FORMATS,
    EMPTY_DRAFT,
    articleFieldsOf,
    draftOf,
    withValue,
    type ArticleDraft,
    type ArticleFieldPath,
  } from '$lib/api/article-form';
  import {
    createArticle,
    getArticle,
    updateArticle,
    type AdminArticleDetail,
    type AdminArticleTag,
    type ApiResult,
  } from '$lib/api/client';
  import {
    NO_ERRORS,
    formErrorsOf,
    hasAssignedErrors,
    type FormErrors,
  } from '$lib/api/form-errors';
  import { ARTICLE_TYPE_LABELS } from '$lib/article-labels';
  import { KEY_STORE, storedApiKey } from '$lib/credentials';
  import { renderBody } from '$lib/markup';
  import { ARTICLE_LIST_PATH, articleIdIn, editArticlePath } from '$lib/paths';

  /**
   * 記事の新規作成・編集。
   *
   * <p>
   * 検証エラーの位置（`field`）は管理APIが入力パスで返す（#288）。この画面は欄の綴りをその入力パスに
   * 揃えており、code から欄を導く対応表を持たない。位置に対応する欄が無いエラーは捨てず、位置を添えて
   * 全体のエラーとして出す（DECISIONS 29）。
   * </p>
   *
   * <p>
   * **保存しても画面を離れない。** 作った直後に続けて直せることのほうが、一覧へ戻ることより多い。保存の
   * たびに保存後の世代を持ち直し、次の保存もその世代を条件に送る（#287）。
   * </p>
   *
   * <p>
   * 本文は入力しながら描画結果を確かめられる。描画は公開サイトと同じ共有の関数を通す（DECISIONS 24）。
   * </p>
   *
   * <p>
   * タグと作品への参照は持たない（#309 の別スライス）。種別は選べるが、`ALBUM` を選んでも参照を
   * 付ける操作はまだ無い。
   * </p>
   */
  type Props = {
    /**
     * 対象の与えられ方。
     *
     * `edit` は問い合わせ文字列から対象を読む（静的な成果物のため、記事ごとの経路を持てない）。
     */
    readonly mode: 'new' | 'edit';
  };

  const { mode }: Props = $props();

  /** 入力欄1つの宣言。位置の綴りは管理APIの入力パスと同じ */
  type FieldSpec = Readonly<{
    path: ArticleFieldPath;
    label: string;
    kind: 'text' | 'multiline' | 'choice';
    /** 選択肢。`choice` 以外では空 */
    choices: readonly string[];
  }>;

  const FIELDS: readonly FieldSpec[] = [
    { path: 'articleType', label: '種別', kind: 'choice', choices: ARTICLE_TYPES },
    { path: 'title', label: 'タイトル', kind: 'text', choices: [] },
    { path: 'introShort', label: 'ショート紹介文', kind: 'multiline', choices: [] },
    { path: 'body', label: '本文', kind: 'multiline', choices: [] },
    { path: 'bodyFormat', label: '本文の形式', kind: 'choice', choices: BODY_FORMATS },
  ];

  /** 欄を持つ位置。ここに無い位置のエラーは、欄へ割り当てず全体へ出す */
  const ASSIGNABLE_PATHS: readonly string[] = FIELDS.map((field) => field.path);

  /** 選択肢の表示。種別は一覧と同じ語を使う */
  const CHOICE_LABELS: Readonly<Record<string, string>> = {
    ...ARTICLE_TYPE_LABELS,
    PLAIN_TEXT: 'プレーンテキスト',
    MARKDOWN: 'Markdown',
  };

  /**
   * 保存の状態。
   *
   * <p>
   * **検証で拒まれた（`invalid`）と、それ以外の理由で保存できなかった（`refused`）を同じ枝にしない。**
   * 前者は欄へ割り当てられる位置つきのエラーを持ち、直す先が入力にある。後者（通信断・応答の契約違反）は
   * 入力を直しても変わらない。
   * </p>
   *
   * <p>
   * `saved` を持つのは、保存しても画面を離れないため。離れる作りなら画面が変わることが結果の合図になるが、
   * 留まる作りでは何も起きなかったのと見分けがつかない。
   * </p>
   *
   * <p>
   * `detached` は**作成はできたが、続けて編集するための世代を読めなかった**状態。ここから保存を通すと
   * 同じ内容の記事をもう1件作ることになるため、保存を塞いで読み直しへ導く。
   * </p>
   *
   * <p>
   * `confirming` は、保存すると作品への参照が落ちることを確かめている状態。落とすかどうかを決めるのは
   * バックエンドの遷移規則で、画面は**そうなることを伝える**だけ。
   * </p>
   */
  type Submission =
    | { readonly kind: 'idle' }
    | { readonly kind: 'saving' }
    | { readonly kind: 'saved' }
    | { readonly kind: 'confirming' }
    | { readonly kind: 'invalid'; readonly errors: FormErrors }
    | { readonly kind: 'conflicted' }
    | { readonly kind: 'refused'; readonly message: string }
    | { readonly kind: 'detached'; readonly articleId: string; readonly message: string };

  /**
   * 更新する対象。
   *
   * <p>
   * 世代（`revision`）は読み込んだ時点のもので、保存の条件として送り返す（#287）。新規作成では持たない
   * （まだ無いものに世代は無い）。対象と世代を別々の項目にすると、対象があるのに世代が無い状態を作れて
   * しまう。
   * </p>
   */
  type Target = Readonly<{ articleId: string; revision: number }>;

  /**
   * 鍵待ちのときに抱えている入力。
   *
   * <p>
   * 保存が断られた（401/403）ときも入力は失わない。**鍵の正しさは入力の正しさとは別**であり、鍵を
   * 入れ直せば同じ入力から続けられる必要がある。
   * </p>
   */
  type Pending = Readonly<{
    target: Target | null;
    draft: ArticleDraft;
    tags: readonly AdminArticleTag[];
    albumId: string | null;
  }>;

  /**
   * 鍵を入れ直した後に続けること。
   *
   * <p>
   * 断られたのが何だったかで、続きが変わる。保存なら抱えている入力へ、記事の読み込みならその記事へ、
   * まだ何も始まっていなければ画面の開き方へ戻る。**続きを画面の開き方（`mode`）から導かない**——
   * 新規作成として開いた画面でも、作成した後に読み直すのはその記事であり、開き方へ戻せば空の新規作成
   * に落ちる。そこから保存すれば同じ内容の記事がもう1件できる。
   * </p>
   */
  type Resumption =
    | { readonly kind: 'input'; readonly pending: Pending }
    | { readonly kind: 'load'; readonly articleId: string }
    | { readonly kind: 'open' };

  /**
   * 画面の状態。
   *
   * 鍵待ち・読み込み中・読めなかった・対象が指定されていない・編集中を1つの型で表す。編集中だけが
   * 入力値を持つため、入力値を状態の外に置かない（読み込みに失敗した状態で入力欄が出ることを作らない）。
   */
  type View =
    | {
        readonly kind: 'locked';
        readonly message: string | null;
        /** 鍵を入れ直した後に続けること */
        readonly resumption: Resumption;
      }
    | { readonly kind: 'loading' }
    /*
     * 読めなかった状態。読もうとした対象を抱える——やり直しの先は「画面の開き方」ではなく、失敗した
     * その読み込みである。開き方へ戻すと、作成済みの記事を読み直している途中の失敗から、空の新規作成
     * へ落ちる。
     */
    | {
        readonly kind: 'unavailable';
        readonly apiKey: string;
        readonly articleId: string;
        readonly message: string;
      }
    | { readonly kind: 'unspecified' }
    | {
        readonly kind: 'editing';
        readonly apiKey: string;
        /** 更新する対象と、読み込んだ時点の世代。null は新規作成 */
        readonly target: Target | null;
        readonly draft: ArticleDraft;
        /**
         * 付いているタグ。
         *
         * 入力（`draft`）と別に持つ。タグは本体の保存とは別の経路で変わるため、保存の対象に含めない。
         */
        readonly tags: readonly AdminArticleTag[];
        /**
         * 参照している作品。無ければ null。
         *
         * タグと同じく本体の保存とは別の経路で変わる。**種別を `ALBUM` から変えると落ちる**ため、
         * 保存の前に確認するかどうかの判断にも使う。
         */
        readonly albumId: string | null;
        readonly submission: Submission;
      };

  let view = $state<View>({ kind: 'loading' });

  /** 失敗した結果 */
  type ApiFailure = Exclude<ApiResult<unknown>, { readonly kind: 'ok' }>;

  /** 失敗の文言。文言の出所を1つにするため、どの操作の失敗もここを通す */
  const failureTextOf = (failure: ApiFailure): string =>
    failure.kind === 'unauthorized' ? '鍵が受け付けられませんでした。' : failure.message;

  const editing = (
    apiKey: string,
    target: Target | null,
    draft: ArticleDraft,
    tags: readonly AdminArticleTag[],
    albumId: string | null,
  ): View => ({
    kind: 'editing',
    apiKey,
    target,
    draft,
    tags,
    albumId,
    submission: { kind: 'idle' },
  });

  const lock = (message: string | null, resumption: Resumption): void => {
    view = { kind: 'locked', message, resumption };
  };

  /**
   * 照会が返した作品への参照。
   *
   * 参照を持てるのは `ALBUM` 種別だけで、応答も種別ごとのサブタイプに分かれている（DECISIONS 21）。
   * 種別で枝を選ぶ以上のことはしない。
   */
  const albumIdOf = (article: AdminArticleDetail): string | null =>
    article.articleType === 'ALBUM' ? article.albumId : null;

  const loaded = (
    apiKey: string,
    articleId: string,
    result: ApiResult<AdminArticleDetail>,
  ): View =>
    result.kind === 'ok'
      ? editing(
          apiKey,
          { articleId, revision: result.value.revision },
          draftOf(result.value),
          result.value.tags,
          albumIdOf(result.value),
        )
      : result.kind === 'unauthorized'
        ? {
            kind: 'locked',
            message: failureTextOf(result),
            /* 断られたのは、この記事の読み込みである。鍵を入れ直したら同じ記事を読み直す */
            resumption: { kind: 'load', articleId },
          }
        : { kind: 'unavailable', apiKey, articleId, message: failureTextOf(result) };

  const load = async (apiKey: string, articleId: string): Promise<void> => {
    view = { kind: 'loading' };

    const result = await getArticle(apiKey, articleId);
    KEY_STORE[result.kind](apiKey);
    view = loaded(apiKey, articleId, result);
  };

  /** 新規作成は読み込むものが無い。鍵だけを確かめて入力へ入る */
  const start = (apiKey: string): void => {
    view = editing(apiKey, null, EMPTY_DRAFT, [], null);
  };

  const unspecify = (): void => {
    view = { kind: 'unspecified' };
  };

  /** 状態を差し替えるだけで、待つものが無い経路。読み込みと同じ形（`Promise`）に揃える */
  const settled = (change: () => void): Promise<void> => {
    change();
    return Promise.resolve();
  };

  /**
   * 対象ごとの開き方。
   *
   * 新規作成に対象は無く、編集は問い合わせ文字列から読む。指定が無いまま編集の画面へ来た場合は、
   * 空の新規作成へ落とさない（何も指定していない更新として保存され得る）。
   */
  const openEdit = (apiKey: string, articleId: string | null): Promise<void> =>
    articleId === null ? settled(unspecify) : load(apiKey, articleId);

  const OPEN = {
    new: (apiKey: string): Promise<void> =>
      settled(() => {
        start(apiKey);
      }),
    edit: (apiKey: string): Promise<void> => openEdit(apiKey, articleIdIn(location.search)),
  } satisfies Record<Props['mode'], (apiKey: string) => Promise<void>>;

  const open = (apiKey: string): Promise<void> => OPEN[mode](apiKey);

  /** 鍵待ちが抱えている続き。鍵待ち以外から呼ばれることはないが、そのときは開き方に従う */
  const resumptionOf = (current: View): Resumption =>
    current.kind === 'locked' ? current.resumption : { kind: 'open' };

  /**
   * 鍵を受け取ったときの続け方。
   *
   * <p>
   * 断られたのが保存なら、入力を抱えたままなので読み直さずその入力へ復帰する（読み直すと、鍵が断られる
   * 前に書いていた内容を捨てることになる）。断られたのが記事の読み込みなら、その記事を読み直す。どちらも
   * 無いときだけ、画面の開き方に従う。
   * </p>
   */
  const accept = (apiKey: string): Promise<void> => {
    const resumption = resumptionOf(view);

    return resumption.kind === 'input'
      ? settled(() => {
          view = editing(
            apiKey,
            resumption.pending.target,
            resumption.pending.draft,
            resumption.pending.tags,
            resumption.pending.albumId,
          );
        })
      : resumption.kind === 'load'
        ? load(apiKey, resumption.articleId)
        : open(apiKey);
  };

  /*
   * このコンポーネントは client:only で載るため、ここが動くのはブラウザだけになる（組み立ての時点で
   * sessionStorage と location を触らない）。
   */
  const resume = (): Promise<void> => {
    const apiKey = storedApiKey();
    return apiKey === null
      ? settled(() => {
          lock(null, { kind: 'open' });
        })
      : open(apiKey);
  };

  void resume();

  /** 読めなかった読み込みをやり直す。戻る先は失敗したその対象で、画面の開き方ではない */
  const retry = (): void => {
    const current = view;
    void (current.kind === 'unavailable'
      ? load(current.apiKey, current.articleId)
      : Promise.resolve());
  };

  /**
   * 保存できたことの合図を消す。
   *
   * 入力を変えた時点で、その合図が指しているのは画面にある内容ではなくなる。検証エラーと競合は残す
   * （直す先が入力にあり、直している最中に消すと何を直しているのか分からなくなる）。
   */
  const afterEdit = (submission: Submission): Submission =>
    submission.kind === 'saved' ? { kind: 'idle' } : submission;

  const update = (path: ArticleFieldPath, value: string): void => {
    const current = view;
    view =
      current.kind === 'editing'
        ? {
            ...current,
            draft: withValue(current.draft, path, value),
            submission: afterEdit(current.submission),
          }
        : current;
  };

  /** 保存の状態だけを差し替えた画面。入力値は保つ（直す先が入力にあるため、消さない） */
  const withSubmissionOf = (submission: Submission): View => {
    const current = view;
    return current.kind === 'editing' ? { ...current, submission } : current;
  };

  const withSubmission = (submission: Submission): void => {
    view = withSubmissionOf(submission);
  };

  /**
   * 保存できなかった結果を、直す先で分ける。
   *
   * 欄へ割り当てられる位置が1つでもあれば検証エラーとして扱う。400 でも位置が無い（要求全体に関わる
   * もの、欄を持たない位置のもの）場合は、全体のエラーとして出す枝へ渡る。
   */
  const rejection = (failure: ApiFailure, errors: FormErrors): Submission =>
    [hasAssignedErrors(errors), errors.unassigned.length > 0].some(Boolean)
      ? { kind: 'invalid', errors }
      : { kind: 'refused', message: failureTextOf(failure) };

  /** 競合として返る状態コード。編集を始めた後に別の操作が保存している（#287） */
  const CONFLICT_STATUS = 409;

  /**
   * 競合は入力の誤りと分けて扱う。
   *
   * 直す先が入力ではなく「読み直し」にあるため、欄へも全体のエラーへも出さない。古い値を自動で再送も
   * しない（同じ世代で送り直せば再び競合する）。
   */
  const rejectionOf = (failure: ApiFailure): Submission =>
    failure.status === CONFLICT_STATUS
      ? { kind: 'conflicted' }
      : rejection(failure, formErrorsOf(failure.problem, ASSIGNABLE_PATHS));

  /**
   * 作った記事を、そのまま編集し続けられる形にする。
   *
   * <p>
   * 作成の応答は世代を返さないため、管理向け詳細を引き直して得る。世代を推測すると、最初の保存が
   * 編集の間に入った別の保存を消しかねない。
   * </p>
   */
  const attach = async (apiKey: string, articleId: string): Promise<ApiResult<Target>> => {
    const result = await getArticle(apiKey, articleId);

    return result.kind === 'ok'
      ? { kind: 'ok', value: { articleId, revision: result.value.revision } }
      : result;
  };

  /**
   * 保存できたときに残る、次の保存の条件。
   *
   * <p>
   * 作成も更新も、保存後の世代を持った対象を返す。**作成の失敗と、作成の後で世代を読めなかったことを
   * 区別する**ため、作った記事のIDと、読めなかった理由を添えて返す。前者は入力が残るだけだが、後者は
   * 記事が既に存在している。
   * </p>
   */
  type Saved = Readonly<{
    target: Target | null;
    /** 作成でできた記事のID。更新では null */
    createdArticleId: string | null;
    /** 世代を読めなかった理由。読めていれば null */
    detachedReason: string | null;
  }>;

  const created = async (apiKey: string, draft: ArticleDraft): Promise<ApiResult<Saved>> => {
    const result = await createArticle(apiKey, articleFieldsOf(draft));

    return result.kind === 'ok' ? attachedTo(apiKey, result.value.articleId) : result;
  };

  /**
   * 作った記事に、画面を結び付ける。
   *
   * <p>
   * 経路をその記事の編集へ差し替えてから世代を引く。読み込み直したときに同じ記事が開くようにするため
   * で、世代を引けたかどうかとは関わりなく、記事はもう存在している。履歴は積まない——戻る操作で
   * 「新規作成の画面」へ戻しても、その記事はもう作られている。
   * </p>
   */
  const attachedTo = async (apiKey: string, articleId: string): Promise<ApiResult<Saved>> => {
    history.replaceState(null, '', editArticlePath(articleId));

    const result = await attach(apiKey, articleId);

    return result.kind === 'ok'
      ? {
          kind: 'ok',
          value: { target: result.value, createdArticleId: articleId, detachedReason: null },
        }
      : {
          kind: 'ok',
          value: {
            target: null,
            createdArticleId: articleId,
            detachedReason: failureTextOf(result),
          },
        };
  };

  const updated = async (
    apiKey: string,
    target: Target,
    draft: ArticleDraft,
  ): Promise<ApiResult<Saved>> => {
    const result = await updateArticle(
      apiKey,
      target.articleId,
      articleFieldsOf(draft),
      target.revision,
    );

    return result.kind === 'ok'
      ? {
          kind: 'ok',
          value: {
            target: { articleId: target.articleId, revision: result.value.revision },
            createdArticleId: null,
            detachedReason: null,
          },
        }
      : result;
  };

  /**
   * 保存の経路。
   *
   * 更新は編集を始めた時点の世代を条件として送る。新規作成に世代は無い（まだ無いものは誰も更新できない）。
   */
  const save = (
    apiKey: string,
    target: Target | null,
    draft: ArticleDraft,
  ): Promise<ApiResult<Saved>> =>
    target === null ? created(apiKey, draft) : updated(apiKey, target, draft);

  /** いま抱えている入力。編集中でなければ持たない */
  /**
   * 保存が断られたときに、鍵を入れ直した後で続けること。
   *
   * 入力を抱えていればそれへ戻る。保存の経路は編集中からしか通らないため、それ以外は起きない。
   */
  const resumptionAfterSave = (current: View): Resumption =>
    current.kind === 'editing'
      ? {
          kind: 'input',
          pending: {
            target: current.target,
            draft: current.draft,
            tags: current.tags,
            albumId: current.albumId,
          },
        }
      : { kind: 'open' };

  /**
   * タグの操作が鍵で断られたとき。
   *
   * 保存が断られたときと同じ扱いにする。断られたのは鍵で、入力の正しさとは別のことであり、入れ直せば
   * 続けられる必要がある。
   */
  const lockWithInput = (message: string): void => {
    view = { kind: 'locked', message, resumption: resumptionAfterSave(view) };
  };

  /** 付け外しの結果を画面全体へ取り込む。抱えている記事の状態と食い違わせない */
  const withTags = (next: readonly AdminArticleTag[]): void => {
    const current = view;
    view = current.kind === 'editing' ? { ...current, tags: next } : current;
  };

  /**
   * 参照の変更を画面全体へ取り込む。種別を変えるときの確認も、この値で決まる。
   *
   * <p>
   * **参照の設定・解除は記事そのものを更新するため、世代が進む。**付け外しの応答は保存後の世代を返すため、
   * それをそのまま次の条件にする。**GETで世代を取り直さない**——取り直すと、間に入った別の保存を黙って
   * 追い越すことになる（#323）。
   * </p>
   */
  const withAlbum = (next: string | null, revision: number): void => {
    const current = view;
    view =
      current.kind === 'editing'
        ? {
            ...current,
            albumId: next,
            target: current.target === null ? current.target : { ...current.target, revision },
          }
        : current;
  };

  const viewAfterFailure = (failure: ApiFailure): View =>
    failure.kind === 'unauthorized'
      ? {
          kind: 'locked',
          message: failureTextOf(failure),
          resumption: resumptionAfterSave(view),
        }
      : withSubmissionOf(rejectionOf(failure));

  /**
   * 保存できたときの画面。
   *
   * <p>
   * 対象を持てていれば、保存後の世代を抱えて留まる。持てていない（作成はできたが世代を読めなかった）
   * ときは保存を塞ぐ——そのまま送ると同じ内容の記事をもう1件作る。
   * </p>
   */
  const detachedOf = (saved: Saved): Submission => ({
    kind: 'detached',
    articleId: saved.createdArticleId ?? '',
    message: `記事は作成されましたが、続きを読み込めませんでした。${saved.detachedReason ?? ''}`,
  });

  /**
   * 保存した後に残っている参照。
   *
   * <p>
   * `ALBUM` 以外で保存すれば参照は落ちる。**落とすと決めるのはバックエンド**だが、保存の前に確かめた
   * 結果をここへ取り込まないと、種別を戻したときに落ちた参照を残っているように見せる。
   * </p>
   */
  const albumAfterSave = (current: Extract<View, { readonly kind: 'editing' }>): string | null =>
    current.draft.articleType === 'ALBUM' ? current.albumId : null;

  const viewAfterSave = (saved: Saved): View => {
    const current = view;

    return current.kind !== 'editing'
      ? current
      : saved.target === null
        ? { ...current, submission: detachedOf(saved) }
        : {
            ...current,
            target: saved.target,
            albumId: albumAfterSave(current),
            submission: { kind: 'saved' },
          };
  };

  const applySaveOutcome = (apiKey: string, result: ApiResult<Saved>): void => {
    KEY_STORE[result.kind](apiKey);
    view = result.kind === 'ok' ? viewAfterSave(result.value) : viewAfterFailure(result);
  };

  const submitWith = async (
    apiKey: string,
    target: Target | null,
    draft: ArticleDraft,
  ): Promise<void> => {
    withSubmission({ kind: 'saving' });
    applySaveOutcome(apiKey, await save(apiKey, target, draft));
  };

  /** 保存へ進める状態。送信中と、作成後に世代を読めていない状態からは送らない */
  const SUBMITTABLE_KINDS = ['idle', 'saved', 'invalid', 'conflicted', 'refused'] as const;

  const submittable = (submission: Submission): boolean =>
    SUBMITTABLE_KINDS.some((kind) => kind === submission.kind);

  /**
   * 保存すると作品への参照が落ちるか。
   *
   * <p>
   * 参照を持てるのは `ALBUM` 種別だけのため、参照があるまま他の種別へ変えて保存すると落ちる
   * （DECISIONS 21。落とすのはバックエンドの遷移規則で、画面はその判定を持たない）。**入力の誤りでは
   * ないので欄のエラーにはせず、保存の前に確かめる。**
   * </p>
   */
  const dropsAlbum = (current: Extract<View, { readonly kind: 'editing' }>): boolean =>
    [current.albumId !== null, current.draft.articleType !== 'ALBUM'].every(Boolean);

  const submit = (event: SubmitEvent): void => {
    event.preventDefault();

    const current = view;
    void (current.kind === 'editing' && submittable(current.submission)
      ? requested(current)
      : Promise.resolve());
  };

  /** 確かめることがあれば確認へ、無ければそのまま保存へ */
  const requested = (current: Extract<View, { readonly kind: 'editing' }>): Promise<void> =>
    dropsAlbum(current)
      ? settled(() => {
          withSubmission({ kind: 'confirming' });
        })
      : submitWith(current.apiKey, current.target, current.draft);

  /** 参照が落ちることを確かめたうえで保存する */
  const confirmSave = (): void => {
    const current = view;
    void (current.kind === 'editing' && current.submission.kind === 'confirming'
      ? submitWith(current.apiKey, current.target, current.draft)
      : Promise.resolve());
  };

  /** 確認をやめる。入力も参照もそのまま残る */
  const cancelSave = (): void => {
    const current = view;
    void (current.kind === 'editing' && current.submission.kind === 'confirming'
      ? settled(() => {
          withSubmission({ kind: 'idle' });
        })
      : Promise.resolve());
  };

  /**
   * いま編集している記事。
   *
   * <p>
   * 対象を持っていればそれで、持っていなくても作成はできていれば（`detached`）その記事。どちらも
   * 無いときだけ、まだ何も作られていない。**画面をどう開いたか（`mode`）はここに関わらない**——
   * 新規作成として開いた画面でも、作成した後に指しているのはその記事である。
   * </p>
   */
  const editedArticleIdOf = (current: View): string | null =>
    current.kind !== 'editing'
      ? null
      : current.target !== null
        ? current.target.articleId
        : current.submission.kind === 'detached'
          ? current.submission.articleId
          : null;

  /**
   * 最新を読み込み直す。
   *
   * <p>
   * 競合したときと、作成の後で世代を読めなかったときの復帰先。いまの入力は保存されている内容に
   * 置き換わる（差分の突き合わせはまだ持たない。#287 の受け入れは「古い値を自動で再送しない」まで）。
   * </p>
   *
   * <p>
   * 読み直す先は**いま編集している記事**で、画面の開き方ではない。開き方へ戻すと、新規作成として
   * 開いた画面で作成した後の読み直しが、空の新規作成へ落ちる。そこから保存すれば同じ内容の記事が
   * もう1件できる。
   * </p>
   */
  const reload = (): void => {
    const current = view;
    const articleId = editedArticleIdOf(current);

    void (current.kind !== 'editing'
      ? Promise.resolve()
      : articleId === null
        ? open(current.apiKey)
        : load(current.apiKey, articleId));
  };

  /*
   * NARROWING-IN-TEMPLATE: テンプレートの分岐は型の絞り込みを持ち越せないため、状態から取り出した
   * 値をここで用意する。
   */
  const lockMessage = $derived(view.kind === 'locked' ? view.message : null);

  /** 入力を抱えたまま鍵待ちへ戻っていることを伝える文言。抱えていなければ出さない */
  const pendingNotice = $derived(
    view.kind === 'locked' && view.resumption.kind === 'input'
      ? '入力した内容は保持しています。鍵を入れ直すと、続けて保存できます。'
      : null,
  );
  const unavailableMessage = $derived(view.kind === 'unavailable' ? view.message : null);
  const draft = $derived<ArticleDraft>(view.kind === 'editing' ? view.draft : EMPTY_DRAFT);
  const target = $derived<Target | null>(view.kind === 'editing' ? view.target : null);
  const apiKey = $derived(view.kind === 'editing' ? view.apiKey : '');
  const tags = $derived<readonly AdminArticleTag[]>(view.kind === 'editing' ? view.tags : []);

  /** タグを付ける先。まだ作られていなければ null */
  const taggedArticleId = $derived(target === null ? null : target.articleId);
  const albumId = $derived(view.kind === 'editing' ? view.albumId : null);

  /** 参照の区画を出すか。参照を持てるのは `ALBUM` 種別だけ（DECISIONS 21） */
  const albumReferable = $derived(draft.articleType === 'ALBUM');

  const submission = $derived<Submission>(
    view.kind === 'editing' ? view.submission : { kind: 'idle' },
  );
  const errors = $derived<FormErrors>(
    submission.kind === 'invalid' ? submission.errors : NO_ERRORS,
  );
  const refusedMessage = $derived(submission.kind === 'refused' ? submission.message : null);
  const detachedMessage = $derived(submission.kind === 'detached' ? submission.message : null);
  const saving = $derived(submission.kind === 'saving');
  const conflicted = $derived(submission.kind === 'conflicted');
  const savedNotice = $derived(submission.kind === 'saved' ? '保存しました。' : null);

  /** 参照が落ちることの確認を出しているか */
  const confirming = $derived(submission.kind === 'confirming');

  /** 入力と保存を塞ぐ条件。送信中と、作成後に世代を読めていない状態 */
  const blocked = $derived([saving, submission.kind === 'detached'].some(Boolean));

  const messagesOf = (path: ArticleFieldPath): readonly string[] => errors.byField.get(path) ?? [];

  /** 欄の識別子 */
  const idOf = (path: ArticleFieldPath): string => `article-${path}`;

  /** 保存の文言。対象を持てば更新で、持たなければ作成（画面の開き方ではなく、いまの対象で決まる） */
  const saveLabel = $derived(target === null ? '作成する' : '保存する');

  /**
   * 本文のプレビュー。
   *
   * <p>
   * 描画は公開サイトと同じ共有の関数を通す（DECISIONS 24）。ここで別の描画を持つと、プレビューが
   * 嘘になる。プレーンテキストは記法として解釈せず、そのまま出す（公開サイトと同じ扱い）ため、
   * この値は null になる。
   * </p>
   */
  const previewHtml = $derived(draft.bodyFormat === 'MARKDOWN' ? renderBody(draft.body) : null);
</script>

{#if view.kind === 'locked'}
  <div class="space-y-4">
    {#if pendingNotice !== null}
      <p class="text-muted-foreground text-sm">{pendingNotice}</p>
    {/if}
    <ApiKeyForm message={lockMessage} onSubmit={(apiKey: string) => void accept(apiKey)} />
  </div>
{:else if view.kind === 'loading'}
  <p class="text-muted-foreground">読み込んでいます。</p>
{:else if view.kind === 'unspecified'}
  <div class="space-y-4">
    <p class="text-destructive" role="alert">編集する記事が指定されていません。</p>
    <a class="text-sm underline underline-offset-4" href={ARTICLE_LIST_PATH}>一覧へ戻る</a>
  </div>
{:else if unavailableMessage !== null}
  <div class="max-w-md space-y-4">
    <p class="text-destructive" role="alert">{unavailableMessage}</p>
    <div class="flex items-center gap-4">
      <Button type="button" onclick={retry}>再試行</Button>
      <a class="text-sm underline underline-offset-4" href={ARTICLE_LIST_PATH}>一覧へ戻る</a>
    </div>
  </div>
{:else}
  <!--
    入力とプレビューを横に並べる。プレビューは打ちながら確かめるためのもので、入力の下に置くと
    本文を打っている間は画面の外にある。狭い画面では縦に積む（横に並べる幅が無い）。
  -->
  <div class="grid items-start gap-8 lg:grid-cols-2">
    <div class="space-y-8">
      <!--
        保存のフォームは本体の項目だけを含む。タグと作品への参照は押した時点で反映される別の操作で、
        同じフォームへ入れると、そちらの入力欄で Enter を押したときに本体が保存される。
      -->
      <form class="space-y-8" onsubmit={submit}>
        <!--
          送ったのはクリックした時点の入力である。保存中も入力を受け付けると、その後の変更は要求に
          入らないまま、保存できたことになる。
        -->
        <fieldset class="space-y-4" disabled={blocked}>
          {#each FIELDS as field (field.path)}
            <div class="space-y-1" data-field={field.path}>
              <label class="text-sm font-medium" for={idOf(field.path)}>{field.label}</label>

              {#if field.kind === 'choice'}
                <select
                  id={idOf(field.path)}
                  class="border-input bg-background w-full rounded-md border px-3 py-2"
                  value={draft[field.path]}
                  aria-invalid={messagesOf(field.path).length > 0}
                  onchange={(event) => {
                    update(field.path, event.currentTarget.value);
                  }}
                >
                  {#each field.choices as choice (choice)}
                    <option value={choice}>{CHOICE_LABELS[choice] ?? choice}</option>
                  {/each}
                </select>
              {:else if field.kind === 'multiline'}
                <textarea
                  id={idOf(field.path)}
                  class="border-input bg-background w-full rounded-md border px-3 py-2"
                  rows="6"
                  value={draft[field.path]}
                  aria-invalid={messagesOf(field.path).length > 0}
                  oninput={(event) => {
                    update(field.path, event.currentTarget.value);
                  }}></textarea>
              {:else}
                <input
                  id={idOf(field.path)}
                  class="border-input bg-background w-full rounded-md border px-3 py-2"
                  type="text"
                  value={draft[field.path]}
                  aria-invalid={messagesOf(field.path).length > 0}
                  oninput={(event) => {
                    update(field.path, event.currentTarget.value);
                  }}
                />
              {/if}

              {#each messagesOf(field.path) as message (message)}
                <p class="text-destructive text-sm" role="alert">{message}</p>
              {/each}
            </div>
          {/each}
        </fieldset>

        {#if errors.unassigned.length > 0}
          <section class="space-y-1">
            <h2 class="text-base font-medium">どの項目にも紐付かないエラー</h2>
            {#each errors.unassigned as message (message)}
              <p class="text-destructive text-sm" role="alert">{message}</p>
            {/each}
          </section>
        {/if}

        {#if conflicted}
          <section class="space-y-2" role="alert">
            <h2 class="text-destructive text-base font-medium">
              編集を始めた後に、別の操作がこの記事を保存しています
            </h2>
            <p class="text-muted-foreground text-sm">
              いまの入力はそのまま保持しています。このまま保存し直しても、同じ理由で断られます。最新を読み込むと、
              入力は保存されている内容に置き換わります。
            </p>
            <Button type="button" variant="outline" onclick={reload}>最新を読み込む</Button>
          </section>
        {/if}

        {#if detachedMessage !== null}
          <section class="space-y-2" role="alert">
            <h2 class="text-destructive text-base font-medium">{detachedMessage}</h2>
            <p class="text-muted-foreground text-sm">
              このまま保存すると、同じ内容の記事をもう1件作ることになります。読み込み直すと、作られた記事を
              続けて編集できます。
            </p>
            <Button type="button" variant="outline" onclick={reload}>読み込み直す</Button>
          </section>
        {/if}

        {#if refusedMessage !== null}
          <p class="text-destructive text-sm" role="alert">{refusedMessage}</p>
        {/if}

        {#if savedNotice !== null}
          <p class="text-muted-foreground text-sm" role="status">{savedNotice}</p>
        {/if}

        <div class="flex items-center gap-4">
          <Button type="submit" disabled={blocked}>
            {saving ? '保存しています…' : saveLabel}
          </Button>
          <a class="text-sm underline underline-offset-4" href={ARTICLE_LIST_PATH}>一覧へ戻る</a>
        </div>
      </form>

      <!--
        対象が変わったら作り直す（作成の直後に、その記事のタグと参照を引き直すため）。区画の中の状態は
        記事ごとのもので、前の記事の候補や失敗を持ち越さない。
      -->
      {#key taggedArticleId}
        <ArticleTagsEditor
          {apiKey}
          {tags}
          articleId={taggedArticleId}
          onUnauthorized={lockWithInput}
          onChanged={withTags}
        />

        {#if albumReferable}
          <ArticleAlbumEditor
            {apiKey}
            {albumId}
            articleId={taggedArticleId}
            expectedRevision={target === null ? null : target.revision}
            onUnauthorized={lockWithInput}
            onChanged={withAlbum}
          />
        {/if}
      {/key}
    </div>

    <!--
      スクロールに追従させる。入力の側が長く、下の欄を触っているときも描画結果を見ていられる。
      本文が伸びてもプレビュー自体は画面に収め、中だけをスクロールさせる。
    -->
    <section class="space-y-2 lg:sticky lg:top-6">
      <h2 class="text-base font-medium">本文のプレビュー</h2>
      <p class="text-muted-foreground text-sm">
        公開サイトと同じ描画を通しています。余白や文字の大きさは、公開サイトの見えかたとは別です。
      </p>

      {#if previewHtml === null}
        <div
          class="prose-plain border-input max-h-[70vh] overflow-y-auto rounded-md border px-3 py-2"
          data-preview="plain"
        >
          {draft.body}
        </div>
      {:else}
        <div
          class="prose-body border-input max-h-[70vh] overflow-y-auto rounded-md border px-3 py-2"
          data-preview="markdown"
        >
          <!-- eslint-disable-next-line svelte/no-at-html-tags -- 出すのは共有の描画（packages/markup）がサニタイズ済みの HTML で、生HTMLをパースする経路は入口で塞いである（DECISIONS 24）。描画結果を出すことがこの区画の目的で、テキストとして出せばプレビューにならない -->
          {@html previewHtml}
        </div>
      {/if}
    </section>
  </div>

  {#if confirming}
    <ConfirmDialog
      open={true}
      title="この記事は作品を参照しています"
      description="この種別のまま保存すると、参照は失効します。参照を残すなら、種別を作品紹介へ戻してください。"
      confirmLabel="このまま保存する"
      confirmDisabled={false}
      cancelDisabled={false}
      running={false}
      failureMessage={null}
      onRetry={null}
      onConfirm={confirmSave}
      onCancel={cancelSave}
    />
  {/if}
{/if}
