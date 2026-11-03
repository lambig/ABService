import type { AdminArticleDetail, ApiResult } from '$lib/api/client';
import { render, screen } from '@testing-library/svelte';
import { userEvent } from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

/** 管理APIとの継ぎ目だけを差し替え、保存中と失敗後の入力状態を確かめる。 */
const { getArticle, updateArticle, createArticle, listArticleTags, listSiteContents } = vi.hoisted(
  () => ({
    getArticle: vi.fn(),
    updateArticle: vi.fn(),
    createArticle: vi.fn(),
    listArticleTags: vi.fn<typeof import('$lib/api/client').listArticleTags>(),
    listSiteContents: vi.fn<typeof import('$lib/api/client').listSiteContents>(),
  }),
);

vi.mock('$lib/api/client', () => ({
  getArticle,
  updateArticle,
  createArticle,
  listArticleTags,
  listSiteContents,
  addArticleTag: vi.fn(),
  removeArticleTag: vi.fn(),
  getAlbum: vi.fn(),
  searchAlbums: vi.fn(),
  setArticleAlbum: vi.fn(),
  removeArticleAlbum: vi.fn(),
}));

const { default: ArticleForm } = await import('./ArticleForm.svelte');

const ARTICLE_ID = '01a0233d-d25a-7c3b-924f-236ee154fecc';
const detail = {
  articleId: ARTICLE_ID,
  articleType: 'NOTE',
  revision: 3,
  title: '記事',
  body: '本文',
  bodyFormat: 'PLAIN_TEXT',
  introShort: '紹介文',
  publishedAt: null,
  updatedAtBusiness: null,
  publicFlag: false,
  tags: [],
} satisfies AdminArticleDetail;

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  window.history.replaceState({}, '', `/?articleId=${ARTICLE_ID}`);
  sessionStorage.setItem(
    'abservice.admin.session',
    JSON.stringify({
      token: 'abs_session_test',
      expiresAt: new Date(Date.now() + 1_800_000).toISOString(),
    }),
  );
  getArticle.mockResolvedValue({ kind: 'ok', value: detail });
  listArticleTags.mockResolvedValue({ kind: 'ok', value: [] });
  listSiteContents.mockResolvedValue({ kind: 'ok', value: [] });
});

describe('記事の保存と入力状態', () => {
  it('保存中は入力と二重送信を防ぎ、通信失敗後は入力を保って編集を再開できる', async () => {
    const pending = Promise.withResolvers<ApiResult<unknown>>();
    updateArticle.mockReturnValueOnce(pending.promise);
    render(ArticleForm, { mode: 'edit' });
    const title = await screen.findByLabelText('タイトル');
    const body = screen.getByLabelText('本文', { exact: true });
    const intro = screen.getByLabelText('ショート紹介文');
    const type = screen.getByLabelText('種別');
    const format = screen.getByLabelText('本文の形式');
    await userEvent.type(title, '改');
    await userEvent.type(body, 'を編集');
    await userEvent.type(intro, 'を編集');
    await userEvent.selectOptions(type, 'NEWS');
    await userEvent.selectOptions(format, 'MARKDOWN');
    await userEvent.click(screen.getByRole('button', { name: '保存する' }));

    [title, body, intro, type, format].forEach((field) => {
      expect(field.matches(':disabled')).toBe(true);
    });
    const saving = screen.getByRole('button', { name: '保存しています…' });
    expect(saving).toHaveProperty('disabled', true);
    await userEvent.click(saving);
    expect(updateArticle).toHaveBeenCalledTimes(1);

    pending.resolve({ kind: 'failed', reason: 'network', message: '通信できませんでした。' });
    expect(await screen.findByRole('alert')).toHaveProperty(
      'textContent',
      '通信できませんでした。',
    );
    expect(title).toHaveProperty('value', '記事改');
    expect(body).toHaveProperty('value', '本文を編集');
    expect(intro).toHaveProperty('value', '紹介文を編集');
    expect(type).toHaveProperty('value', 'NEWS');
    expect(format).toHaveProperty('value', 'MARKDOWN');
    [title, body, intro, type, format].forEach((field) => {
      expect(field.matches(':disabled')).toBe(false);
    });
    expect(screen.getByRole('button', { name: '保存する' })).toHaveProperty('disabled', false);
    await userEvent.type(title, '続');
    expect(title).toHaveProperty('value', '記事改続');
    expect(updateArticle).toHaveBeenCalledTimes(1);
    expect(createArticle).not.toHaveBeenCalled();
  });
});
