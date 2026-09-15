<script lang="ts">
  import { onDestroy } from 'svelte';
  import { PUBLIC_ASSET_BASE_PATH } from 'astro:env/client';
  import { renderArticle, renderPageFrame, renderSiteNav } from 'abservice-public-presentation';
  import { Button } from '$components/ui/button/index.js';
  import * as Dialog from '$components/ui/dialog/index.js';
  import {
    getAlbum,
    listSiteContents,
    type AdminAlbumDetail,
    type AdminArticleTag,
    type ApiResult,
  } from '$lib/api/client';
  import type { ArticleDraft } from '$lib/api/article-form';
  import { applySessionResult, type AdminSession } from '$lib/credentials';
  import { ARTICLE_PREVIEW_PATH } from '$lib/paths';

  /** 保存前の入力を公開ページの器で確認する。作品とサイト文言は開くたびに取り直す。 */
  type Props = Readonly<{
    session: AdminSession;
    draft: ArticleDraft;
    tags: readonly AdminArticleTag[];
    albumId: string | null;
    publishedAt: string | null;
    disabled: boolean;
    onUnauthorized: () => void;
  }>;
  const { session, draft, tags, albumId, publishedAt, disabled, onUnauthorized }: Props = $props();
  type View =
    | { readonly kind: 'closed' }
    | { readonly kind: 'loading' }
    | { readonly kind: 'failed'; readonly message: string }
    | { readonly kind: 'ready'; readonly html: string };
  let view = $state<View>({ kind: 'closed' });
  let generation = $state(0);
  let narrow = $state(false);
  let frame = $state<HTMLIFrameElement>();

  const close = (): void => {
    generation += 1;
    view = { kind: 'closed' };
  };
  onDestroy(close);

  const show = async (): Promise<void> => {
    generation += 1;
    const request = generation;
    const currentSession = session;
    const article = { ...draft, tags: tags.map((tag) => tag.name), publishedAt };
    view = { kind: 'loading' };
    const [site, album] = await Promise.all([
      listSiteContents(currentSession),
      albumId === null
        ? Promise.resolve<ApiResult<AdminAlbumDetail | null>>({ kind: 'ok', value: null })
        : getAlbum(currentSession, albumId),
    ]);
    const result: ApiResult<unknown> =
      album.kind === 'unauthorized' ? album : site.kind !== 'ok' ? site : album;
    return request !== generation
      ? undefined
      : applySessionResult(currentSession, result, () => {
          view =
            site.kind === 'ok' && album.kind === 'ok'
              ? {
                  kind: 'ready',
                  html: renderPageFrame(
                    {
                      name: site.value.find((item) => item.key === 'site.name')?.content ?? 'Site',
                      copyrightHolder:
                        site.value.find((item) => item.key === 'footer.copyright.holder')
                          ?.content ?? null,
                      year: new Date().getFullYear(),
                      isHome: false,
                    },
                    renderArticle({ ...article, album: album.value }, PUBLIC_ASSET_BASE_PATH),
                    renderSiteNav(),
                  ),
                }
              : {
                  kind: 'failed',
                  message: 'プレビューに必要な情報を読み込めませんでした。もう一度お試しください。',
                };
          [result]
            .filter((value) => value.kind === 'unauthorized')
            .forEach(() => {
              onUnauthorized();
            });
        });
  };

  /** 同一オリジンでもフレーム内のスクリプト実行は許可しない。本文は共有のサニタイザーを通した値のみ。 */
  const paint = (): void => {
    const document = frame?.contentDocument;
    const container = document?.getElementById('article-preview');
    /* eslint-disable functional/immutable-data -- ブラウザ境界: 共有描画の結果を隔離した表示先へ反映する。 */
    void (container !== undefined && container !== null && view.kind === 'ready'
      ? (container.innerHTML = view.html)
      : undefined);
    /* eslint-enable functional/immutable-data -- DOM への反映はここまで。 */
    view =
      container == null
        ? {
            kind: 'failed',
            message: 'プレビューの画面を読み込めませんでした。もう一度お試しください。',
          }
        : view;
    const stopNavigation = (event: Event): void => {
      const element = event.target as Element | null;
      [element]
        .filter((value) => value?.closest('a') != null)
        .forEach(() => {
          event.preventDefault();
        });
    };
    document?.addEventListener('click', stopNavigation);
    document?.addEventListener('auxclick', stopNavigation);
    document?.addEventListener('keydown', (event) => {
      [event]
        .filter((value) => value.key === 'Escape')
        .forEach((value) => {
          value.preventDefault();
          close();
        });
    });
  };
  const openChange = (open: boolean): void => {
    [
      close,
      () => {
        void show();
      },
    ][Number(open)]?.();
  };
</script>

<Dialog.Root open={view.kind !== 'closed'} onOpenChange={openChange}>
  <Dialog.Trigger>
    {#snippet child({ props })}
      <Button {...props} type="button" variant="outline" {disabled}
        >公開レイアウトでプレビュー</Button
      >
    {/snippet}
  </Dialog.Trigger>
  <Dialog.Content
    class="flex h-[95dvh] max-w-[calc(100%-1rem)] flex-col gap-3 p-3 sm:max-w-[calc(100%-2rem)]"
    showCloseButton={false}
  >
    <Dialog.Header>
      <Dialog.Title>公開レイアウトのプレビュー</Dialog.Title>
      <Dialog.Description
        >開いた時点の入力を表示しています。保存・公開は行いません。リンク先への移動はできません。</Dialog.Description
      >
    </Dialog.Header>
    <div class="flex flex-wrap items-center gap-3">
      <Button
        type="button"
        variant="outline"
        aria-pressed={narrow}
        onclick={() => {
          narrow = [narrow].includes(false);
        }}>狭い幅で確認</Button
      >
      <Button type="button" onclick={close}>編集へ戻る</Button>
      {#if publishedAt === null}<p class="text-muted-foreground text-sm">
          未公開のため公開日は表示しません。
        </p>{/if}
    </div>
    {#if view.kind === 'loading'}
      <p role="status">プレビューを準備しています。</p>
    {:else if view.kind === 'failed'}
      <p role="alert">{view.message}</p>
      <Button type="button" variant="outline" onclick={() => void show()}>再試行</Button>
    {:else if view.kind === 'ready'}
      <iframe
        bind:this={frame}
        title="公開記事のプレビュー"
        class="mx-auto min-h-0 flex-1 border"
        style:width={narrow ? 'min(390px, 100%)' : '100%'}
        src={ARTICLE_PREVIEW_PATH}
        sandbox="allow-same-origin"
        referrerpolicy="no-referrer"
        onload={paint}
      ></iframe>
    {/if}
  </Dialog.Content>
</Dialog.Root>
