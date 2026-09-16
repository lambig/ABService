import { describe, expect, it, vi } from 'vitest';
import { adminApi, toUpdateAlbumRequest } from './index.ts';
import type { AdminAlbumDetail } from './index.ts';

/** 応答を並べた順に返す fetch。送った要求は後から読める */
const fetchReturning = (responses: readonly Response[]) => {
  const queue = [...responses];
  const calls: { url: string; init: RequestInit | undefined }[] = [];
  const send = vi.fn((input: string | URL, init?: RequestInit) => {
    calls.push({ url: input.toString(), init });
    const next = queue.shift();
    return Promise.resolve(next ?? Response.json({}, { status: 500 }));
  });
  return { send: send as unknown as typeof fetch, calls };
};

const connection = (send: typeof fetch) =>
  adminApi({ baseUrl: 'http://backend.test', apiKey: 'secret', fetch: send });

/** クライアントは本文を JSON 文字列で送るため、それ以外が来たら検査として落とす */
const bodyOf = (call: { init: RequestInit | undefined }): unknown => {
  const body = call.init?.body;
  return typeof body === 'string' ? JSON.parse(body) : expect.unreachable('本文が文字列ではない');
};

describe('管理APIクライアント', () => {
  it('管理APIへの要求に鍵を Bearer で付ける', async () => {
    const { send, calls } = fetchReturning([Response.json({ items: [] })]);
    await connection(send).findAlbumByCatalogNumber('CAT-1');

    expect(calls[0]?.url).toBe(
      'http://backend.test/api/v1/admin/albums?size=100&catalogNumber=CAT-1',
    );
    expect(calls[0]?.init?.headers).toMatchObject({ Authorization: 'Bearer secret' });
  });

  it('カタログナンバーの部分一致の結果から、完全一致だけを選ぶ', async () => {
    const { send } = fetchReturning([
      Response.json({
        items: [
          { albumId: 'a-10', catalogNumber: 'CAT-10', publishedAt: null },
          { albumId: 'a-1', catalogNumber: 'CAT-1', publishedAt: '2026-01-01T00:00:00Z' },
        ],
      }),
    ]);

    await expect(connection(send).findAlbumByCatalogNumber('CAT-1')).resolves.toEqual({
      albumId: 'a-1',
      catalogNumber: 'CAT-1',
      publishedAt: '2026-01-01T00:00:00Z',
    });
  });

  it('アセットは払い出し・署名付きURLへの送信・確定の3段を順に通す', async () => {
    const { send, calls } = fetchReturning([
      Response.json({ assetKey: 'key-1', uploadUrl: 'http://storage.test/pending/key-1' }),
      new Response(null, { status: 200 }),
      Response.json({ assetKey: 'key-1' }),
    ]);
    const body = new Blob(['png-bytes'], { type: 'image/png' });

    await expect(connection(send).seedAsset({ contentType: 'image/png', body })).resolves.toBe(
      'key-1',
    );

    expect(calls.map((call) => call.url)).toEqual([
      'http://backend.test/api/v1/assets/upload-url',
      'http://storage.test/pending/key-1',
      'http://backend.test/api/v1/assets/key-1/confirm',
    ]);
    expect(calls[1]?.init?.method).toBe('PUT');
    expect(calls[1]?.init?.headers).toEqual({ 'Content-Type': 'image/png' });
    expect(calls[1]?.init?.body).toBe(body);
  });

  it('保管先へ送れなかった実体は確定へ進めない', async () => {
    const { send, calls } = fetchReturning([
      Response.json({ assetKey: 'key-1', uploadUrl: 'http://storage.test/pending/key-1' }),
      new Response(null, { status: 403 }),
    ]);

    await expect(
      connection(send).seedAsset({ contentType: 'image/png', body: new Blob(['x']) }),
    ).rejects.toThrow('HTTP 403');
    expect(calls).toHaveLength(2);
  });

  it('作品は曲目と外部音源ごと1リクエストで作り、画像があれば先に確定する', async () => {
    const { send, calls } = fetchReturning([
      Response.json({ assetKey: 'cover', uploadUrl: 'http://storage.test/pending/cover' }),
      new Response(null, { status: 200 }),
      Response.json({ assetKey: 'cover' }),
      Response.json({ albumId: 'album-1' }),
    ]);

    const albumId = await connection(send).seedDraftAlbum({
      title: 'T',
      releaseDate: '2026-01-01',
      artistDisplayName: 'A',
      artistSortKey: 'a',
      catalogNumber: 'CAT-1',
      coverImage: { contentType: 'image/png', body: new Blob(['x']) },
      tracks: [{ title: 'Track', tunes: [{ tuneTitle: 'Tune' }, {}] }, { tunes: [] }],
      externalAudioUrls: ['https://soundcloud.com/x/y'],
    });

    expect(albumId).toBe('album-1');
    expect(calls[3]?.url).toBe('http://backend.test/api/v1/albums/with-tracks');
    expect(bodyOf(calls[3] ?? { init: undefined })).toMatchObject({
      title: 'T',
      catalogNumber: 'CAT-1',
      coverImageKey: 'cover',
      tracks: [{ title: 'Track', tunes: [{ tuneTitle: 'Tune' }, {}] }, { tunes: [] }],
      externalAudios: [{ url: 'https://soundcloud.com/x/y' }],
    });
  });

  it('作品参照やタグが付かなかった記事は消してから失敗を伝え、途中の下書きを残さない', async () => {
    const { send, calls } = fetchReturning([
      Response.json({ articleId: 'article-1' }),
      Response.json({}),
      Response.json({}),
      new Response('{"detail":"tag too long"}', { status: 400 }),
      new Response(null, { status: 204 }),
    ]);

    await expect(
      connection(send).seedDraftArticle({
        articleType: 'ALBUM',
        title: 'Article',
        albumId: 'album-1',
        tags: ['one', 'x'.repeat(101)],
      }),
    ).rejects.toThrow('POST /api/v1/articles/article-1/tags が失敗しました（HTTP 400）');

    expect(calls.map((call) => `${call.init?.method ?? 'GET'} ${call.url}`)).toEqual([
      'POST http://backend.test/api/v1/articles',
      'PUT http://backend.test/api/v1/articles/article-1/album',
      'POST http://backend.test/api/v1/articles/article-1/tags',
      'POST http://backend.test/api/v1/articles/article-1/tags',
      'DELETE http://backend.test/api/v1/articles/article-1',
    ]);
  });

  it('途中の下書きを消せなかったときは、その記事のIDを添えて元の失敗を伝える', async () => {
    const { send } = fetchReturning([
      Response.json({ articleId: 'article-1' }),
      new Response('{"detail":"no such album"}', { status: 404 }),
      new Response(null, { status: 500 }),
    ]);

    await expect(
      connection(send).seedDraftArticle({
        articleType: 'ALBUM',
        title: 'Article',
        albumId: 'gone',
      }),
    ).rejects.toThrow(
      /PUT \/api\/v1\/articles\/article-1\/album が失敗しました（HTTP 404）.*\n途中まで組み立てた記事 article-1 を消せませんでした。手で消してから再実行してください: DELETE/u,
    );
  });

  it('記事は作成のあとに作品参照とタグを1件ずつ付ける', async () => {
    const { send, calls } = fetchReturning([
      Response.json({ articleId: 'article-1' }),
      Response.json({}),
      Response.json({}),
      Response.json({}),
    ]);

    await connection(send).seedDraftArticle({
      articleType: 'ALBUM',
      title: 'Article',
      albumId: 'album-1',
      tags: ['one', 'two'],
    });

    expect(calls.map((call) => call.url)).toEqual([
      'http://backend.test/api/v1/articles',
      'http://backend.test/api/v1/articles/article-1/album',
      'http://backend.test/api/v1/articles/article-1/tags',
      'http://backend.test/api/v1/articles/article-1/tags',
    ]);
    expect(bodyOf(calls[1] ?? { init: undefined })).toEqual({
      albumId: 'album-1',
      expectedRevision: 0,
    });
    expect(bodyOf(calls[2] ?? { init: undefined })).toEqual({ name: 'one' });
  });

  it('記事の一覧は全ページたぐってからタイトルで選ぶ', async () => {
    const { send, calls } = fetchReturning([
      Response.json({
        items: [{ articleId: 'x', title: 'X', publishedAt: null }],
        totalElements: 2,
        totalPages: 2,
      }),
      Response.json({
        items: [{ articleId: 'y', title: 'Y', publishedAt: null }],
        totalElements: 2,
        totalPages: 2,
      }),
    ]);

    await expect(connection(send).findArticleByTitle('Y')).resolves.toEqual({
      articleId: 'y',
      title: 'Y',
      publishedAt: null,
    });
    expect(calls).toHaveLength(2);
  });

  it('失敗の応答は状態と本文を持つ例外になる', async () => {
    const { send } = fetchReturning([new Response('{"detail":"no"}', { status: 409 })]);

    await expect(connection(send).publishAlbum('album-1')).rejects.toThrow(
      'POST /api/v1/albums/album-1/publish が失敗しました（HTTP 409）: {"detail":"no"}',
    );
  });

  it('詳細から作る全項目置換の要求は、子のIDと並びを保ち null の項目を持たない', () => {
    const detail: AdminAlbumDetail = {
      albumId: 'album-1',
      revision: 3,
      title: 'T',
      releaseDate: '2026-01-01',
      artistDisplayName: 'A',
      artistSortKey: null,
      description: null,
      descriptionFormat: 'PLAIN_TEXT',
      catalogNumber: 'CAT-1',
      isdn: null,
      eventName: 'Event',
      eventDate: null,
      eventPlace: 'Place',
      eventSpaceNumber: null,
      eventNote: null,
      publishedAt: null,
      coverImageKey: 'cover',
      coverImageUrl: '/assets/cover',
      basePrice: null,
      originalWorkNote: null,
      externalAudios: [
        { externalAudioId: 'ea-1', displayOrder: 1, url: 'https://soundcloud.com/x' },
      ],
      tracks: [
        {
          trackId: 'track-1',
          trackNo: 1,
          title: null,
          artistDisplayName: null,
          artistSortKey: null,
          displayTitle: 'Tune',
          tunes: [
            {
              seq: 1,
              tuneTitle: 'Tune',
              composerCreditOverride: null,
              arrangerCreditOverride: null,
              linkUrl: null,
            },
          ],
        },
      ],
    };

    expect(toUpdateAlbumRequest(detail)).toStrictEqual({
      expectedRevision: 3,
      title: 'T',
      releaseDate: '2026-01-01',
      artistDisplayName: 'A',
      catalogNumber: 'CAT-1',
      coverImageKey: 'cover',
      descriptionFormat: 'PLAIN_TEXT',
      event: { name: 'Event', place: 'Place' },
      tracks: [{ trackId: 'track-1', tunes: [{ tuneTitle: 'Tune' }] }],
      externalAudios: [{ externalAudioId: 'ea-1', url: 'https://soundcloud.com/x' }],
    });
  });
});
