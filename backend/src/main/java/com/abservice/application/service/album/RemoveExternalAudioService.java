package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.album.ExternalAudio;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.domain.service.AlbumExistenceService;
import com.abservice.lib.Result;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;

/**
 * 外部音源削除コマンドサービス
 *
 * <p>
 * {@link Album#removeExternalAudio(ExternalAudio.Id)}
 * を呼び出すユースケースです。対象が存在しない場合はAlbum集約自身が
 * {@link com.abservice.domain.exception.BusinessRuleViolationException}（409）で検証します
 * （削除対象アルバム集約自体の不在確認とは異なり、べき等な削除ではありません）。残る外部音源の表示順は詰め直されます。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class RemoveExternalAudioService
        implements
            CommandService<RemoveExternalAudioInput, RemoveExternalAudioOutput> {

    private final AlbumRepository albumRepository;
    private final AlbumExistenceService albumExistenceService;

    @WithTransaction
    @Override
    public Uni<RemoveExternalAudioOutput> execute(RemoveExternalAudioInput input) {
        return Uni.createFrom()
                .item(
                        () -> Result.zip(
                                Album.Id.fromInput(input.albumId()),
                                ExternalAudio.Id.fromInput(input.externalAudioId()),
                                Ids::new)
                                .resolve(ValidationException::new))
                .flatMap(
                        ids -> albumExistenceService.findExisting(ids.albumId())
                                .map(album -> album.removeExternalAudio(ids.externalAudioId()))
                                .flatMap(albumRepository::save)
                                .map(saved -> toOutput(saved, ids.externalAudioId())));
    }

    private record Ids(Album.Id albumId, ExternalAudio.Id externalAudioId) {
    }

    private static RemoveExternalAudioOutput toOutput(Album album, ExternalAudio.Id externalAudioId) {
        return new RemoveExternalAudioOutput(album.id().value(), externalAudioId.value());
    }
}
