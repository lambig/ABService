package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import com.abservice.application.service.album.ReorderTracksOutput.TrackOrderEntry;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.album.Track;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.domain.service.AlbumExistenceService;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * トラック順序変更コマンドサービス
 *
 * <p>
 * {@link Album#reorderTracks(List)} を呼び出すユースケースです。指定順序のトラック数がアルバムの保持する
 * トラック数と一致しない場合はAlbum集約自身が検証します（{@link com.abservice.domain.exception.BusinessRuleViolationException}、409）。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class ReorderTracksService implements CommandService<ReorderTracksInput, ReorderTracksOutput> {

    private final AlbumRepository albumRepository;
    private final AlbumExistenceService albumExistenceService;

    @WithTransaction
    @Override
    public Uni<ReorderTracksOutput> execute(ReorderTracksInput input) {
        return Uni.createFrom()
                .item(
                        () -> Result.zip(
                                Album.Id.fromInput(input.albumId()),
                                validateOrderedTrackIds(input.orderedTrackIds()),
                                Ids::new)
                                .resolve(ValidationException::new))
                .flatMap(
                        ids -> albumExistenceService.findExisting(ids.albumId())
                                .map(album -> album.reorderTracks(ids.orderedTrackIds()))
                                .flatMap(albumRepository::save)
                                .map(ReorderTracksService::toOutput));
    }

    private record Ids(Album.Id albumId, List<Track.Id> orderedTrackIds) {
    }

    static Result<List<Track.Id>> validateOrderedTrackIds(@Nullable List<@Nullable String> values) {
        return Optional.ofNullable(values)
                .map(ReorderTracksService::sequence)
                .orElseGet(
                        () -> Result.failure(
                                new ErrorResult(
                                        "orderedTrackIds",
                                        "Ordered track IDs are required",
                                        "TRACK_ORDER_REQUIRED")));
    }

    private static Result<List<Track.Id>> sequence(List<@Nullable String> values) {
        return values.stream()
                .map(Track.Id::fromInput)
                .reduce(
                        Result.success(List.of()),
                        (acc, next) -> Result.zip(
                                acc,
                                next,
                                ReorderTracksService::append),
                        (a, b) -> Result.zip(
                                a,
                                b,
                                ReorderTracksService::concat));
    }

    private static List<Track.Id> append(List<Track.Id> list, Track.Id id) {
        return Stream.concat(list.stream(), Stream.of(id)).toList();
    }

    private static List<Track.Id> concat(List<Track.Id> a, List<Track.Id> b) {
        return Stream.concat(a.stream(), b.stream()).toList();
    }

    private static ReorderTracksOutput toOutput(Album album) {
        return new ReorderTracksOutput(
                album.id().value(),
                album.getTracksSortedByTrackNo().stream()
                        .map(track -> new TrackOrderEntry(track.id().value(), track.trackNo()))
                        .toList());
    }
}
