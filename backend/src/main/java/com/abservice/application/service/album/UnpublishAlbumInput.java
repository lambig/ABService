package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import com.abservice.application.service.InputPolicy;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.aggregate.album.Album;
import org.jspecify.annotations.Nullable;

/**
 * アルバム非公開化コマンドの入力DTO
 *
 * @param albumId
 *            非公開化対象のアルバムID
 */
public record UnpublishAlbumInput(
        @Nullable String albumId) implements CommandService.ValidatedInput<UnpublishAlbumInput> {

    @Override
    public Policy<UnpublishAlbumInput> policy() {
        return new InputPolicy<>(input -> Album.Id.fromInput(input.albumId())
                .mapErrorFields(field -> "albumId"));
    }
}
