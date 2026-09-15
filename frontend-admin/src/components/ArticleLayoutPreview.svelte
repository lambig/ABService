<script lang="ts">
  import { onDestroy, untrack } from 'svelte';
  import { PUBLIC_ASSET_BASE_PATH } from 'astro:env/client';
  import {
    renderArticle,
    renderArticleHeader,
    renderArticleBody,
    renderPageFrame,
    renderSiteNav,
  } from 'abservice-public-presentation';
  import { Button } from '$components/ui/button/index.js';
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

  /** 入力欄の隣で公開時の体裁を確認する。入力の描画と関連情報の取得を分ける。 */
  type Props = Readonly<{
    session: AdminSession;
    draft: ArticleDraft;
    tags: readonly AdminArticleTag[];
    albumId: string | null;
    publishedAt: string | null;
    onUnauthorized: () => void;
  }>;
  const { session, draft, tags, albumId, publishedAt, onUnauthorized }: Props = $props();
  type View =
    | { readonly kind: 'loading' }
    | { readonly kind: 'failed'; readonly message: string }
    | {
        readonly kind: 'ready';
        readonly name: string;
        readonly copyrightHolder: string | null;
        readonly album: AdminAlbumDetail | null;
        readonly defaultArtistName: string | null;
      };
  let view = $state<View>({ kind: 'loading' });
  let generation = $state(0);
  let frameDocument = $state.raw<Document | null>(null);
  onDestroy(() => {
    generation += 1;
  });

  const load = async (
    currentSession: AdminSession,
    currentAlbumId: string | null,
  ): Promise<void> => {
    generation += 1;
    const request = generation;
    frameDocument = null;
    view = { kind: 'loading' };
    const [site, album] = await Promise.all([
      listSiteContents(currentSession),
      currentAlbumId === null
        ? Promise.resolve<ApiResult<AdminAlbumDetail | null>>({ kind: 'ok', value: null })
        : getAlbum(currentSession, currentAlbumId),
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
                  name: site.value.find((item) => item.key === 'site.name')?.content ?? 'Site',
                  copyrightHolder:
                    site.value.find((item) => item.key === 'footer.copyright.holder')?.content ??
                    null,
                  album: album.value,
                  defaultArtistName:
                    site.value.find((item) => item.key === 'site.artist')?.content ?? null,
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
  $effect(() => {
    const currentSession = session;
    const currentAlbumId = albumId;
    untrack(() => {
      void load(currentSession, currentAlbumId);
    });
  });

  /** 作品の取得結果が変わるときだけ全体を描画し、入力中に試聴を再起動しない。 */
  $effect(() => {
    const document = frameDocument;
    const context = view;
    untrack(() => {
      const container = document?.getElementById('article-preview');
      /* eslint-disable functional/immutable-data -- ブラウザ境界: 共有描画済みHTMLを静的なプレビュー文書へ渡す。 */
      void (container != null && context.kind === 'ready'
        ? (container.innerHTML = renderPageFrame(
            {
              name: context.name,
              copyrightHolder: context.copyrightHolder,
              year: new Date().getFullYear(),
              isHome: false,
            },
            renderArticle(
              {
                ...draft,
                publishedAt,
                tags: tags.map((tag) => tag.name),
                album:
                  context.album === null
                    ? null
                    : {
                        ...context.album,
                        tracks: context.album.tracks.map((track) => ({
                          ...track,
                          title: track.displayTitle,
                        })),
                      },
              },
              PUBLIC_ASSET_BASE_PATH,
              context.defaultArtistName,
            ),
            renderSiteNav(),
          ))
        : undefined);
      /* eslint-enable functional/immutable-data -- 全体のDOM反映はここまで。 */
    });
  });
  /** 見出し・本文だけを更新し、同じ作品のプレイヤーとスクロール位置を保持する。 */
  $effect(() => {
    const regions = [
      [
        '[data-article-header]',
        renderArticleHeader({ ...draft, publishedAt, tags: tags.map((tag) => tag.name) }),
      ],
      ['[data-article-body]', renderArticleBody(draft, PUBLIC_ASSET_BASE_PATH)],
    ] as const;
    regions.forEach(([selector, html]) => {
      const container = frameDocument?.querySelector(selector);
      /* eslint-disable functional/immutable-data -- ブラウザ境界: 共有サニタイザーの出力だけを更新する。 */
      void (container != null && container.innerHTML !== html
        ? (container.innerHTML = html)
        : undefined);
      /* eslint-enable functional/immutable-data -- 入力領域のDOM反映はここまで。 */
    });
  });
  const loaded = (event: Event): void => {
    const document = (event.currentTarget as HTMLIFrameElement).contentDocument;
    frameDocument = document;
    view =
      document?.getElementById('article-preview') == null
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
  };
</script>

<section class="min-w-0 space-y-2 lg:sticky lg:top-6" aria-label="公開レイアウトのプレビュー">
  <h2 class="text-base font-medium">公開レイアウトのプレビュー</h2>
  <p class="text-muted-foreground text-sm">
    入力内容を表示しています。保存・公開は行いません。リンク先への移動はできません。
  </p>
  {#if publishedAt === null}<p class="text-muted-foreground text-sm">
      未公開のため公開日は表示しません。
    </p>{/if}
  {#if view.kind === 'loading'}
    <p role="status">プレビューを準備しています。</p>
  {:else if view.kind === 'failed'}
    <p role="alert">{view.message}</p>
    <Button type="button" variant="outline" onclick={() => void load(session, albumId)}
      >再試行</Button
    >
  {:else}
    <Button type="button" variant="outline" onclick={() => void load(session, albumId)}
      >関連情報を更新</Button
    >
    <iframe
      title="公開記事のプレビュー"
      class="h-[70vh] w-full border"
      src={ARTICLE_PREVIEW_PATH}
      sandbox="allow-same-origin allow-scripts"
      referrerpolicy="no-referrer"
      onload={loaded}
    ></iframe>
  {/if}
</section>
