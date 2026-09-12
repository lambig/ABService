package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import io.smallrye.mutiny.Uni;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * 記事へのAlbum参照設定コマンドの入力DTO
 *
 * @param articleId
 *            対象の記事ID
 * @param albumId
 *            紐付けるアルバムID
 * @param expectedRevision
 *            編集を始めた時点の世代（必須）。紐付けはArticleの世代を進めるため、保存の直前に読んだ世代と違えば
 *            その間に別の操作が保存しているため競合として拒む（DECISIONS 30、#323）
 */
public record SetArticleAlbumInput(
        @Nullable String articleId,
        @Nullable String albumId,
        @Nullable Integer expectedRevision) implements CommandService.Input {

    /**
     * 自身が妥当（{@code articleId}・{@code albumId}が有効な形式で、{@code expectedRevision}が指定済み）
     * であることを検証する
     *
     * <p>
     * 検証責務をCommandService側に持たせず、Input自身が答えられるようにする（#148で
     * {@link com.abservice.domain.model.policy.Policy}を用いた共通デフォルト実装へ移行予定）。
     * </p>
     *
     * @return 検証済みの自身。無効な場合は各フィールドのエラーを集約した{@link ValidationException}で失敗する
     */
    Uni<SetArticleAlbumInput> asValidated() {
        return Uni.createFrom()
                .item(
                        () -> Result.zip(
                                Article.Id.fromInput(articleId)
                                        .mapErrorFields(field -> "articleId"),
                                Album.Id.fromInput(albumId)
                                        .mapErrorFields(field -> "albumId"),
                                expectedRevision(expectedRevision),
                                (parsedArticleId, parsedAlbumId, parsedExpectedRevision) -> this)
                                .resolve(ValidationException::new));
    }

    private static Result<Integer> expectedRevision(@Nullable Integer value) {
        return Optional.ofNullable(value)
                .map(Result::success)
                .orElseGet(
                        () -> Result.failure(
                                new ErrorResult(
                                        "expectedRevision",
                                        "編集を始めた時点の世代は必須です",
                                        "ARTICLE_EXPECTED_REVISION_REQUIRED")));
    }
}
