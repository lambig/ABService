package com.abservice.presentation.rest.article.response;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * 管理向け記事詳細レスポンス（REST の公開出力契約）
 *
 * <p>
 * 管理画面の編集フォームが使う項目をすべて持つ。下書き（{@code publicFlag} が false）とアルバム参照の失効は
 * 編集者が張り直しを判断するための情報であり、ここでだけ返す。
 * </p>
 *
 * <p>
 * 項目名の集合が同一の種別に別の型は与えない。アルバムへの参照を持てるのは {@code ALBUM} だけのため、分かれるのは
 * {@link AdminAlbumArticleDetailResponse} と
 * {@link AdminPlainArticleDetailResponse} の2つになる。
 * </p>
 */
public sealed interface AdminArticleDetailResponse
        permits AdminAlbumArticleDetailResponse, AdminPlainArticleDetailResponse {

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
     * 一覧表示用のショート紹介文（nullable）
     *
     * @return ショート紹介文
     */
    @Nullable
    String introShort();

    /**
     * 公開日時（nullable。null は下書き。UTC）
     *
     * @return 公開日時
     */
    @Nullable
    Instant publishedAt();

    /**
     * 業務上の更新日時（nullable。UTC）
     *
     * @return 更新日時
     */
    @Nullable
    Instant updatedAtBusiness();

    /**
     * 公開フラグ
     *
     * @return 公開中なら true
     */
    boolean publicFlag();
}
