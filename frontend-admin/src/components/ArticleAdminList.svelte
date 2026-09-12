<script lang="ts">
  import ApiKeyForm from '$components/ApiKeyForm.svelte';
  import ConfirmDialog from '$components/ConfirmDialog.svelte';
  import { Badge } from '$components/ui/badge/index.js';
  import { Button } from '$components/ui/button/index.js';
  import * as Table from '$components/ui/table/index.js';
  import {
    EMPTY_PAGE,
    availablePageOf,
    isFirstPage,
    isLastPage,
    rangeOf,
  } from '$lib/api/article-list';
  import {
    deleteArticle,
    listArticles,
    publishArticle,
    unpublishArticle,
    type AdminArticle,
    type AdminArticlePage,
    type ApiResult,
  } from '$lib/api/client';
  import { ARTICLE_TYPE_LABELS } from '$lib/article-labels';
  import { KEY_STORE, forgetApiKey, storedApiKey } from '$lib/credentials';
  import { formatPublishedDate } from '$lib/format';
  import { NEW_ARTICLE_PATH, editArticlePath } from '$lib/paths';

  /**
   * 記事の一覧と、公開・非公開・削除。
   *
   * <p>
   * 下書きを含むため、中身はブラウザが管理APIから引く。応答が返したページ情報を保ち、1ページに
   * 収まらない記事も辿れるようにする（#309）。
   * </p>
   *
   * <p>
   * 確認を挟むのは削除だけ。公開状態の切り替えは同じ画面から戻せるが、削除は戻せない。作品の削除と
   * 違い、影響範囲の照会は無い——記事を指す集約が無く、問うべき前提が無いため（DECISIONS 27）。
   * </p>
   */

  /** 一覧から起こせる操作 */
  type Operation = 'publish' | 'unpublish' | 'delete';

  /**
   * 一覧の上で進んでいること。
   *
   * <p>
   * `idle` 以外の間は、どの行の操作も受け付けない。送信中に同じ操作を重ねられると、先に投げた要求の
   * 結果が後から返って、後の結果を上書きし得る。
   * </p>
   *
   * <p>
   * `confirming` の `message` は**直前の実行が断られた理由**で、対話を開いたまま実行し直せることを
   * 表す。実行前（まだ一度も送っていない）は null。
   * </p>
   */
  type Activity =
    | { readonly kind: 'idle' }
    | {
        readonly kind: 'confirming';
        readonly article: AdminArticle;
        readonly message: string | null;
      }
    | {
        readonly kind: 'running';
        readonly article: AdminArticle;
        readonly operation: Operation;
      };

  /**
   * 画面の状態。
   *
   * 鍵待ち・読み込み中・一覧・失敗を1つの型で表す。個別の真偽値を並べると、読み込み中でありながら
   * 一覧も出ている、といった表せてはいけない組み合わせが作れてしまう。
   */
  type View =
    /*
     * 鍵待ち。入れ直した後に読むページを抱える——鍵が断られるのは一覧を引くときだけでなく行を操作した
     * ときもあり、そこで先頭へ戻すと、後ろのページでしていた作業の位置を失う。鍵の正しさと作業位置は
     * 別のことである。
     */
    | { readonly kind: 'locked'; readonly message: string | null; readonly page: number }
    | { readonly kind: 'loading' }
    | {
        readonly kind: 'ready';
        readonly apiKey: string;
        readonly page: AdminArticlePage;
        readonly activity: Activity;
      }
    /*
     * 鍵を持ったまま失敗した状態。同じ鍵でやり直せるようにするため、ここで抱える。見ていたページも
     * 抱える——失敗のたびに先頭へ戻すと、後ろのページに用があった操作をやり直せない。
     */
    | {
        readonly kind: 'failed';
        readonly message: string;
        readonly apiKey: string;
        readonly page: number;
      };

  let view = $state<View>({ kind: 'locked', message: null, page: 0 });

  const assign = (next: View): void => {
    view = next;
  };

  /** 状態を差し替えるだけで、待つものが無い経路。読み込みと同じ形（`Promise`）に揃える */
  const settled = (change: () => void): Promise<void> => {
    change();
    return Promise.resolve();
  };

  /** 失敗した結果 */
  type ApiFailure = Exclude<ApiResult<unknown>, { readonly kind: 'ok' }>;

  /** 失敗の文言。文言の出所を1つにするため、どの操作の失敗もここを通す */
  const failureTextOf = (failure: ApiFailure): string =>
    failure.kind === 'unauthorized' ? '鍵が受け付けられませんでした。' : failure.message;

  const toView = (apiKey: string, requested: number, result: ApiResult<AdminArticlePage>): View =>
    result.kind === 'ok'
      ? { kind: 'ready', apiKey, page: result.value, activity: { kind: 'idle' } }
      : result.kind === 'unauthorized'
        ? { kind: 'locked', message: failureTextOf(result), page: requested }
        : { kind: 'failed', message: failureTextOf(result), apiKey, page: requested };

  /**
   * 求めたページが範囲の外なら読み直す。
   *
   * <p>
   * 最終ページの最後の1件を消した直後がこれに当たる。空の表を出さず、応答が返した総ページ数から
   * 求め直した位置を読む。読み直しの位置は必ず手前へ動くため、繰り返しても止まる。
   * </p>
   */
  const applyListOutcome = async (
    apiKey: string,
    requested: number,
    result: ApiResult<AdminArticlePage>,
  ): Promise<void> => {
    const available = result.kind === 'ok' ? availablePageOf(result.value) : requested;

    return available === requested
      ? settled(() => {
          assign(toView(apiKey, requested, result));
        })
      : load(apiKey, available);
  };

  const load = async (apiKey: string, page: number): Promise<void> => {
    assign({ kind: 'loading' });

    const result = await listArticles(apiKey, page);
    KEY_STORE[result.kind](apiKey);
    return applyListOutcome(apiKey, page, result);
  };

  /*
   * 覚えている鍵があれば、そのまま読みにいく。このコンポーネントは client:only で載るため、ここが
   * 動くのはブラウザだけになる（組み立ての時点で sessionStorage を触らない）。
   */
  const resume = async (): Promise<void> => {
    const apiKey = storedApiKey();
    return apiKey === null ? undefined : load(apiKey, 0);
  };

  void resume();

  /* 同じ鍵でやり直す。到達できないだけの失敗は鍵の正しさとは別のため、入力からやり直させない */
  const retry = (): void => {
    const current = view;
    void (current.kind === 'failed' ? load(current.apiKey, current.page) : Promise.resolve());
  };

  /**
   * 鍵を捨てて入力へ戻る。
   *
   * <p>
   * 戻る先は先頭のページにする。鍵を断られたときと違い、これは操作した本人が離れることを選んだ場面
   * であり、続きの作業位置を持ち越す前提を置かない。
   * </p>
   */
  const lock = (): void => {
    forgetApiKey();
    assign({ kind: 'locked', message: null, page: 0 });
  };

  const currentActivity = (): Activity => {
    const current = view;
    return current.kind === 'ready' ? current.activity : { kind: 'idle' };
  };

  /* 進行中のことは一覧の中にあるため、差し替えも一覧の状態を保ったまま行う */
  const withActivity = (activity: Activity): void => {
    const current = view;
    assign(current.kind === 'ready' ? { ...current, activity } : current);
  };

  const RUN = {
    publish: publishArticle,
    unpublish: unpublishArticle,
    delete: deleteArticle,
  } satisfies Record<Operation, (apiKey: string, articleId: string) => Promise<ApiResult<unknown>>>;

  /**
   * 断られたときの戻り先。
   *
   * <p>
   * 削除は対話を開いたまま理由を出す（そこから実行し直せる）。公開状態の切り替えは対話を持たないため、
   * 一覧の失敗として抱える（同じ鍵でやり直せる）。
   * </p>
   */
  const REJECTED = {
    publish: (apiKey: string, _article: AdminArticle, page: number, message: string): void => {
      assign({ kind: 'failed', message, apiKey, page });
    },
    unpublish: (apiKey: string, _article: AdminArticle, page: number, message: string): void => {
      assign({ kind: 'failed', message, apiKey, page });
    },
    delete: (_apiKey: string, article: AdminArticle, _page: number, message: string): void => {
      withActivity({ kind: 'confirming', article, message });
    },
  } satisfies Record<
    Operation,
    (apiKey: string, article: AdminArticle, page: number, message: string) => void
  >;

  const applyRunOutcome = async (
    apiKey: string,
    article: AdminArticle,
    operation: Operation,
    page: number,
    result: ApiResult<unknown>,
  ): Promise<void> =>
    result.kind === 'ok'
      ? load(apiKey, page)
      : result.kind === 'unauthorized'
        ? settled(() => {
            assign({ kind: 'locked', message: failureTextOf(result), page });
          })
        : settled(() => {
            REJECTED[operation](apiKey, article, page, failureTextOf(result));
          });

  const run = async (
    apiKey: string,
    article: AdminArticle,
    operation: Operation,
    page: number,
  ): Promise<void> => {
    withActivity({ kind: 'running', article, operation });

    const result = await RUN[operation](apiKey, article.articleId);
    KEY_STORE[result.kind](apiKey);
    return applyRunOutcome(apiKey, article, operation, page, result);
  };

  /** 進行中は次の操作へ入らない。多重送信で古い結果が新しい状態を上書きしないようにする */
  const startable = (current: View): current is Extract<View, { readonly kind: 'ready' }> =>
    current.kind === 'ready' && current.activity.kind === 'idle';

  const toggle = (article: AdminArticle, operation: 'publish' | 'unpublish'): void => {
    const current = view;
    void (startable(current)
      ? run(current.apiKey, article, operation, current.page.page)
      : Promise.resolve());
  };

  const askDeletion = (article: AdminArticle): void => {
    const current = view;
    withActivity(
      startable(current) ? { kind: 'confirming', article, message: null } : currentActivity(),
    );
  };

  const confirmDeletion = (): void => {
    const current = view;
    const activity = currentActivity();
    void (current.kind === 'ready' && activity.kind === 'confirming'
      ? run(current.apiKey, activity.article, 'delete', current.page.page)
      : Promise.resolve());
  };

  /* 実行中だけ閉じない（送信の結果を受け取る前に閉じると、成否が伝わらない） */
  const cancel = (): void => {
    const activity = currentActivity();
    withActivity(activity.kind === 'running' ? activity : { kind: 'idle' });
  };

  const goTo = (page: number): void => {
    const current = view;
    void (startable(current) ? load(current.apiKey, page) : Promise.resolve());
  };

  /*
   * NARROWING-IN-TEMPLATE: テンプレートの分岐は型の絞り込みを持ち越せないため、状態から取り出した
   * 値をここで用意する。テンプレート側で `view.page` と書くと、型情報を使う検査が解決できない。
   */
  const lockMessage = $derived(view.kind === 'locked' ? view.message : null);

  /** 鍵を入れ直した後に読むページ。鍵待ち以外では使わない */
  const lockedPage = $derived(view.kind === 'locked' ? view.page : 0);
  const failureMessage = $derived(view.kind === 'failed' ? view.message : null);
  const page = $derived<AdminArticlePage>(view.kind === 'ready' ? view.page : EMPTY_PAGE);
  const articles = $derived(page.items);
  const range = $derived(rangeOf(page));
  const activity = $derived<Activity>(view.kind === 'ready' ? view.activity : { kind: 'idle' });

  /** 進行中は一覧の操作を塞ぐ */
  const busy = $derived(activity.kind !== 'idle');

  /** ページ送りを塞ぐ条件。端にいるときと、進行中（読み直しの結果と入れ違いになる） */
  const previousDisabled = $derived([isFirstPage(page), busy].some(Boolean));
  const nextDisabled = $derived([isLastPage(page), busy].some(Boolean));

  /**
   * 削除の対話。
   *
   * 実行中も開いたままにする（結果を受け取る前に閉じない）。開いていなければ null。
   */
  const deletionOf = (
    current: Activity,
  ): Readonly<{ article: AdminArticle; running: boolean; message: string | null }> | null =>
    current.kind === 'confirming'
      ? { article: current.article, running: false, message: current.message }
      : current.kind === 'running' && current.operation === 'delete'
        ? { article: current.article, running: true, message: null }
        : null;

  const deletion = $derived(deletionOf(activity));
</script>

{#if view.kind === 'locked'}
  <ApiKeyForm message={lockMessage} onSubmit={(apiKey: string) => void load(apiKey, lockedPage)} />
{:else if view.kind === 'loading'}
  <p class="text-muted-foreground">読み込んでいます。</p>
{:else if failureMessage !== null}
  <div class="max-w-md space-y-4">
    <p class="text-destructive" role="alert">{failureMessage}</p>

    <div class="flex items-center gap-4">
      <Button type="button" onclick={retry}>再試行</Button>
      <button class="text-sm underline underline-offset-4" type="button" onclick={lock}>
        鍵を破棄する
      </button>
    </div>
  </div>
{:else}
  <div class="space-y-4">
    <div class="flex items-baseline justify-between">
      <p class="text-muted-foreground text-sm">{page.totalElements} 件</p>
      <div class="flex items-center gap-4">
        <a class="text-sm underline underline-offset-4" href={NEW_ARTICLE_PATH}>記事を追加する</a>
        <button class="text-sm underline underline-offset-4" type="button" onclick={lock}>
          鍵を破棄する
        </button>
      </div>
    </div>

    {#if articles.length === 0}
      <p class="text-muted-foreground">登録された記事はありません。</p>
    {:else}
      <Table.Root>
        <Table.Header>
          <Table.Row>
            <Table.Head>タイトル</Table.Head>
            <Table.Head>種別</Table.Head>
            <Table.Head>公開日</Table.Head>
            <Table.Head>状態</Table.Head>
            <Table.Head>操作</Table.Head>
          </Table.Row>
        </Table.Header>
        <Table.Body>
          {#each articles as article (article.articleId)}
            <Table.Row>
              <!-- 文言の長い列だけ折り返させ、幅と行数を抑える（#359）。作品一覧と同じ理由 -->
              <Table.Cell class="w-[34rem] whitespace-normal">
                <span class="line-clamp-2 break-words">{article.title}</span>
              </Table.Cell>
              <Table.Cell>
                {ARTICLE_TYPE_LABELS[article.articleType] ?? article.articleType}
              </Table.Cell>
              <Table.Cell>
                {article.publishedAt === null ? '―' : formatPublishedDate(article.publishedAt)}
              </Table.Cell>
              <Table.Cell>
                <Badge variant={article.publicFlag ? 'default' : 'secondary'}>
                  {article.publicFlag ? '公開' : '下書き'}
                </Badge>
              </Table.Cell>
              <Table.Cell>
                <!-- 操作ごとに幅を固定する（#345）。作品一覧と同じ理由 -->
                <div class="grid grid-cols-[5rem_8rem_auto] items-center gap-2">
                  <a
                    class="text-sm underline underline-offset-4"
                    href={editArticlePath(article.articleId)}
                  >
                    編集する
                  </a>
                  {#if article.publicFlag}
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={busy}
                      onclick={() => {
                        toggle(article, 'unpublish');
                      }}
                    >
                      非公開にする
                    </Button>
                  {:else}
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={busy}
                      onclick={() => {
                        toggle(article, 'publish');
                      }}
                    >
                      公開する
                    </Button>
                  {/if}
                  <Button
                    size="sm"
                    variant="destructive"
                    disabled={busy}
                    onclick={() => {
                      askDeletion(article);
                    }}
                  >
                    削除する
                  </Button>
                </div>
              </Table.Cell>
            </Table.Row>
          {/each}
        </Table.Body>
      </Table.Root>

      <div class="flex items-center justify-between">
        <p class="text-muted-foreground text-sm">{range.first}–{range.last} 件目</p>

        <div class="flex items-center gap-2">
          <Button
            type="button"
            size="sm"
            variant="outline"
            disabled={previousDisabled}
            onclick={() => {
              goTo(page.page - 1);
            }}
          >
            前のページ
          </Button>
          <Button
            type="button"
            size="sm"
            variant="outline"
            disabled={nextDisabled}
            onclick={() => {
              goTo(page.page + 1);
            }}
          >
            次のページ
          </Button>
        </div>
      </div>
    {/if}
  </div>

  {#if deletion !== null}
    <ConfirmDialog
      open={true}
      title="この記事を削除しますか"
      description={`「${deletion.article.title}」を削除すると元に戻せません。`}
      confirmLabel="削除する"
      confirmDisabled={deletion.running}
      cancelDisabled={deletion.running}
      running={deletion.running}
      failureMessage={deletion.message}
      onRetry={null}
      onConfirm={confirmDeletion}
      onCancel={cancel}
    />
  {/if}
{/if}
