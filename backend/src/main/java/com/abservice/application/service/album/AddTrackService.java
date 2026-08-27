package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.album.Track;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.domain.service.AlbumAccessService;
import com.abservice.domain.service.TrackAdditionService;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;

/**
 * トラック追加コマンドサービス
 *
 * <p>
 * {@link Album#addTrack(Track)}
 * を呼び出すユースケースです。値検証・組み立ては{@link TrackAdditionService}
 * （アルバムと同時にトラックを登録する{@code RegisterAlbumWithTracksService}とも共有するドメインサービス）に
 * 委譲します。トラック番号の重複はAlbum集約自身が検証します
 * （{@link com.abservice.domain.exception.BusinessRuleViolationException}、409）。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class AddTrackService implements CommandService<AddTrackInput, AddTrackOutput> {

    private final AlbumRepository albumRepository;
    private final AlbumAccessService albumAccessService;
    private final TrackAdditionService trackAdditionService;

    @WithTransaction
    @Override
    public Uni<AddTrackOutput> execute(AddTrackInput input) {
        return Uni.createFrom()
                .item(
                        () -> Album.Id.fromInput(input.albumId())
                                .resolve(ValidationException::new))
                .flatMap(albumAccessService::findExistingAndClaimEdit)
                .flatMap(
                        album -> trackAdditionService.addTrack(album, toTrackFields(input))
                                .flatMap(
                                        addition -> albumRepository.save(addition.album())
                                                .map(saved -> toOutput(saved, addition.track()))));
    }

    private static TrackAdditionService.TrackFields toTrackFields(AddTrackInput input) {
        return new TrackAdditionService.TrackFields(
                input.trackNo(),
                input.title(),
                input.artistDisplayName(),
                input.artistSortKey());
    }

    private static AddTrackOutput toOutput(Album album, Track track) {
        return new AddTrackOutput(
                album.id().value(),
                track.id().value(),
                track.trackNo(),
                track.title().value());
    }
}
