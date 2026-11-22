package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import com.abservice.application.service.InputPolicy;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.aggregate.album.Album;
import org.jspecify.annotations.Nullable;

/**
 * アルバム削除コマンドの入力DTO
 *
 * @param albumId
 *            削除対象のアルバムID
 */
public record DeleteAlbumInput(@Nullable String albumId) implements CommandService.ValidatedInput<DeleteAlbumInput> {

    @Override
    public Policy<DeleteAlbumInput> policy() {
        return new InputPolicy<>(input -> Album.Id.fromInput(input.albumId())
                .mapErrorFields(field -> "albumId"));
    }
}
