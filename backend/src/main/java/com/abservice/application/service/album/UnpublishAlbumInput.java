package com.abservice.application.service.album;

import com.abservice.lib.Result;
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
        @Nullable String albumId) implements CommandService.Input<UnpublishAlbumInput> {

    @Override
    public Policy<UnpublishAlbumInput> policy() {
        return new InputPolicy<>(UnpublishAlbumInput::parsed);
    }

    Result<Album.Id> parsed() {
        return Album.Id.fromInput(albumId())
                .mapErrorFields(field -> "albumId");
    }
}
