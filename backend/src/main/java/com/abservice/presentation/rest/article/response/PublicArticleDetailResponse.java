package com.abservice.presentation.rest.article.response;

import java.time.Instant;

/**
 * 公開向け記事詳細レスポンス（REST の公開出力契約）
 *
 * <p>
 * 公開サイトの記事詳細ページが使う項目だけを持つ。公開側で起こり得ないこと（下書き・参照の失効）のための項目名は出さない。
 * ショート紹介文はトップ・一覧で使うものであり、詳細では出さない。
 * </p>
 *
 * <p>
 * 項目名の集合が同一の種別に別の型は与えない。アルバムへの参照を持てるのは {@code ALBUM} だけのため、分かれるのは
 * {@link PublicAlbumArticleDetailResponse} と
 * {@link PublicPlainArticleDetailResponse} の2つになる。
 * </p>
 */
public sealed interface PublicArticleDetailResponse
        permits PublicAlbumArticleDetailResponse, PublicPlainArticleDetailResponse {

    /**
     * 記事ID（UUIDv7形式の文字列）
     *
     * @return 記事ID
     */
    String articleId();

    /**
     * 記事種別（列挙子名）
     *
     * @return 記事種別
     */
    String articleType();

    /**
     * 記事タイトル
     *
     * @return タイトル
     */
    String title();

    /**
     * 記事本文（空文字列は本文なし。nullは返さない）
     *
     * @return 本文
     */
    String body();

    /**
     * 本文のマークアップ形式（列挙子名）
     *
     * @return マークアップ形式
     */
    String bodyFormat();

    /**
     * 公開日時（UTC。公開向けは公開中のものだけを返すため常に値を持つ）
     *
     * @return 公開日時
     */
    Instant publishedAt();
}
