package com.abservice.presentation.rest.article.request;

import org.jspecify.annotations.Nullable;

/**
 * 記事更新リクエスト（REST の公開入力契約）
 *
 * <p>
 * PUT風の全項目置換。外部からの未検証入力。値検証はアプリケーション層（各値オブジェクトの {@code fromInput}）に委譲する。
 * </p>
 *
 * @param expectedRevision
 *            編集を始めた時点の世代（必須。管理詳細の {@code revision} をそのまま返す）。保存の直前に読んだ世代と 違えば
 *            409 になる。全項目置換のため、これを持たない更新は編集の間に入った別の保存を消す（DECISIONS 30）
 * @param articleType
 *            記事種別（列挙子名）
 * @param title
 *            記事タイトル
 * @param body
 *            記事本文（nullable。指定時は {@code bodyFormat} も必須）
 * @param bodyFormat
 *            本文のマークアップ形式（列挙子名。{@code body} 指定時のみ必須）
 * @param introShort
 *            一覧表示用のショート紹介文（nullable）
 */
public record UpdateArticleRequest(
        @Nullable Integer expectedRevision,
        @Nullable String articleType,
        @Nullable String title,
        @Nullable String body,
        @Nullable String bodyFormat,
        @Nullable String introShort) {
}
