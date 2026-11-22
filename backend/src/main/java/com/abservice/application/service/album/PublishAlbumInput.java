package com.abservice.application.service.album;

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
public record PublishAlbumInput(@Nullable String albumId) implements CommandService.ValidatedInput<PublishAlbumInput> {

    @Override
    public Policy<PublishAlbumInput> policy() {
        return new InputPolicy<>(input -> Album.Id.fromInput(input.albumId())
                .mapErrorFields(field -> "albumId"));
    }
}
