package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import org.jspecify.annotations.Nullable;

/**
 * 記事作成コマンドの入力DTO
 *
 * <p>
 * 外部（REST 等）からの未検証入力を表現します。すべての値は文字列として受け取り、 検証と型への解釈は
 * {@link CreateArticleService} が {@code Result} 経由で行います。
 * </p>
 *
 * <p>
 * アルバム記事（{@code albumId} を持つ記事）の作成は Album 集約の存在確認を要するため、
 * 本コマンドの対象外です（横展開フェーズで別コマンドとして実装）。
 * </p>
 *
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
public record CreateArticleInput(
        @Nullable String articleType,
        @Nullable String title,
        @Nullable String body,
        @Nullable String bodyFormat,
        @Nullable String introShort) implements CommandService.Input {
}
