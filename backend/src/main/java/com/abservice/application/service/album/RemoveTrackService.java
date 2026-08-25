package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.album.Track;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.domain.service.AlbumAccessService;
import com.abservice.lib.Result;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;

/**
 * トラック削除コマンドサービス
 *
 * <p>
 * {@link Album#removeTrack(Track.Id)} を呼び出すユースケースです。対象トラックが存在しない場合はAlbum集約自身が
 * {@link com.abservice.domain.exception.BusinessRuleViolationException}（409）で検証します
 * （削除対象アルバム集約自体の不在確認とは異なり、べき等な削除ではありません）。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class RemoveTrackService implements CommandService<RemoveTrackInput, RemoveTrackOutput> {

    private final AlbumRepository albumRepository;
    private final AlbumAccessService albumAccessService;

    @WithTransaction
    @Override
    public Uni<RemoveTrackOutput> execute(RemoveTrackInput input) {
        return Uni.createFrom()
                .item(
                        () -> Result.zip(
                                Album.Id.fromInput(input.albumId()),
                                Track.Id.fromInput(input.trackId()),
                                Ids::new)
                                .resolve(ValidationException::new))
                .flatMap(
                        ids -> albumAccessService.findExistingAndClaimEdit(ids.albumId())
                                .map(album -> album.removeTrack(ids.trackId()))
                                .flatMap(albumRepository::save)
                                .map(saved -> toOutput(saved, ids.trackId())));
    }

    private record Ids(Album.Id albumId, Track.Id trackId) {
    }

    private static RemoveTrackOutput toOutput(Album album, Track.Id trackId) {
        return new RemoveTrackOutput(album.id().value(), trackId.value());
    }
}
