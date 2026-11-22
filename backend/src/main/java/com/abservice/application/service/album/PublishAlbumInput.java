package com.abservice.application.service.album;

import com.abservice.lib.Result;
import com.abservice.application.service.CommandService;
import com.abservice.application.service.InputPolicy;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.aggregate.album.Album;
import org.jspecify.annotations.Nullable;

/**
 * アルバム公開コマンドの入力DTO
 *
 * @param albumId
 *            公開対象のアルバムID
 */
public record PublishAlbumInput(@Nullable String albumId) implements CommandService.Input<PublishAlbumInput> {

    @Override
    public Policy<PublishAlbumInput> policy() {
        return new InputPolicy<>(PublishAlbumInput::parsed);
    }

    Result<Album.Id> parsed() {
        return Album.Id.fromInput(albumId())
                .mapErrorFields(field -> "albumId");
    }
}
