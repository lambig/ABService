package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import org.jspecify.annotations.Nullable;

/**
 * 記事更新コマンドの入力DTO
 *
 * <p>
 * PUT風の全項目置換入力です。{@link CreateArticleInput} と同じフィールド範囲
 * （articleType/title/body・bodyFormat/introShort）のみを対象とし、公開状態（{@code publicFlag}）と
 * タグは対象外です（それぞれ専用コマンドの責務のため）。
 * </p>
 *
 * @param articleId
 *            更新対象の記事ID
 * @param articleType
 *            記事種別（{@code com.abservice.domain.model.vo.article.ArticleType}
 *            の列挙子名）
 * @param title
 *            記事タイトル（必須・空不可）
 * @param body
 *            記事本文（nullable。指定する場合は {@code bodyFormat} も必須）
 * @param bodyFormat
 *            本文のマークアップ形式（{@code com.abservice.domain.model.vo.common.MarkupFormat}
 *            の列挙子名。 {@code body} を指定する場合のみ必須）
 * @param introShort
 *            一覧表示用のショート紹介文（nullable）
 */
public record UpdateArticleInput(
        @Nullable String articleId,
        @Nullable String articleType,
        @Nullable String title,
        @Nullable String body,
        @Nullable String bodyFormat,
        @Nullable String introShort) implements CommandService.Input {
}
