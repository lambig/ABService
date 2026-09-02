package com.abservice.presentation.rest.article.response;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 公開向け記事一覧の1件分レスポンス（REST の公開出力契約）
 *
 * <p>
 * 一覧の責務は記事を選ぶための表示であり、本文は詳細（{@link PublicArticleDetailResponse}）で返す。カードに出す
 * ショート紹介文はここで返す。
 * </p>
 *
 * <p>
 * 項目名の集合が同一の種別に別の型は与えない。アルバムへの参照を持てるのは {@code ALBUM} だけのため、分かれるのは
 * {@link PublicAlbumArticleResponse} と {@link PublicPlainArticleResponse}
 * の2つになる。
 * </p>
 */
@Schema(oneOf = {PublicAlbumArticleResponse.class,
        PublicPlainArticleResponse.class}, discriminatorProperty = "articleType")
public sealed interface PublicArticleResponse permits PublicAlbumArticleResponse, PublicPlainArticleResponse {

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
     * 一覧表示用のショート紹介文（空文字列は紹介文なし。nullは返さない）
     *
     * @return ショート紹介文
     */
    String introShort();

    /**
     * 公開日時（UTC。公開向けは公開中のものだけを返すため常に値を持つ）
     *
     * @return 公開日時
     */
    Instant publishedAt();
}
