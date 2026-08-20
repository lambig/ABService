package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.lib.Result;
import io.smallrye.mutiny.Uni;
import org.jspecify.annotations.Nullable;

/**
 * 記事へのAlbum参照設定コマンドの入力DTO
 *
 * @param articleId
 *            対象の記事ID
 * @param albumId
 *            紐付けるアルバムID
 */
public record SetArticleAlbumInput(@Nullable String articleId, @Nullable String albumId)
        implements
            CommandService.Input {

    /**
     * 自身が妥当（{@code articleId}・{@code albumId}が有効な形式）であることを検証する
     *
     * <p>
     * 検証責務をCommandService側に持たせず、Input自身が答えられるようにする（#148で
     * {@link com.abservice.domain.model.policy.Policy}を用いた共通デフォルト実装へ移行予定）。
     * </p>
     *
     * @return 検証済みの自身。無効な場合は両フィールドのエラーを集約した{@link ValidationException}で失敗する
     */
    Uni<SetArticleAlbumInput> asValidated() {
        return Uni.createFrom()
                .item(
                        () -> Result.zip(
                                Article.Id.fromInput(articleId),
                                Album.Id.fromInput(albumId),
                                (parsedArticleId, parsedAlbumId) -> this)
                                .resolve(ValidationException::new));
    }
}
