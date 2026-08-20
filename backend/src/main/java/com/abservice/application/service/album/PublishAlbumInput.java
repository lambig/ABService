package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import io.smallrye.mutiny.Uni;
import org.jspecify.annotations.Nullable;

/**
 * アルバム公開コマンドの入力DTO
 *
 * @param albumId
 *            公開対象のアルバムID
 */
public record PublishAlbumInput(@Nullable String albumId) implements CommandService.Input {

    /**
     * 自身が妥当（{@code albumId}が有効な形式）であることを検証する
     *
     * <p>
     * 検証責務をCommandService側に持たせず、Input自身が答えられるようにする（#148で
     * {@link com.abservice.domain.model.policy.Policy}を用いた共通デフォルト実装へ移行予定）。
     * </p>
     *
     * @return 検証済みの自身。無効な場合は{@link ValidationException}で失敗する
     */
    Uni<PublishAlbumInput> asValidated() {
        return Uni.createFrom()
                .item(
                        () -> Album.Id.fromInput(albumId)
                                .resolve(ValidationException::new))
                .replaceWith(this);
    }
}
