package com.abservice.application.service.album;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.application.exception.ConflictingEditException;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.time.BusinessDate;
import com.abservice.domain.model.vo.credit.ArtistCredit;
import com.abservice.domain.model.vo.markup.MarkupContent;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.domain.service.AlbumAccessService;
import com.abservice.domain.service.AlbumCreationService;
import io.smallrye.mutiny.Uni;
import java.lang.reflect.Proxy;
import java.util.AbstractList;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AlbumCommandInputContractTest {
    private static final class ObservedTracks extends AbstractList<TrackInput> {
        private int reads;

        @Override
        public TrackInput get(int index) {
            Objects.checkIndex(index, size());
            reads++;
            return new TrackInput(
                    null,
                    "track",
                    null,
                    null,
                    null);
        }

        @Override
        public int size() {
            return 1;
        }
    }

    @Test
    @DisplayName("登録処理は購読ごとに曲目を一度だけ解析する")
    void registrationParsesTracksOncePerSubscription() {
        final var tracks = new ObservedTracks();
        final var service = new RegisterAlbumWithTracksService(
                repository(),
                new AlbumCreationService(),
                " / ");
        final var pending = service.execute(
                new RegisterAlbumWithTracksInput(
                        "album",
                        "2026-01-01",
                        "artist",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        tracks,
                        null));
        assertThat(tracks.reads).isZero();
        assertThat(pending.await().indefinitely().tracks()).singleElement()
                .satisfies(track -> assertThat(track.title()).isEqualTo("track"));
        assertThat(tracks.reads).isEqualTo(1);
        pending.await().indefinitely();
        assertThat(tracks.reads).isEqualTo(2);
    }

    @Test
    @DisplayName("更新処理は世代確認後に本文を一度だけ解析する")
    void updateParsesBodyOnceAfterClaimingRevision() {
        final var existing = album();
        final var tracks = new ObservedTracks();
        final var service = new UpdateAlbumService(repository(), access(
                Uni.createFrom().item(
                        new AlbumRepository.Revisioned(existing, new AlbumRepository.Revision(0)))));
        final var pending = service.execute(update(existing, tracks));
        assertThat(tracks.reads).isZero();
        assertThat(pending.await().indefinitely().title()).isEqualTo("updated");
        assertThat(tracks.reads).isEqualTo(1);
    }

    @Test
    @DisplayName("存在しない作品では本文を解析しない")
    void missingAlbumDoesNotParseBody() {
        final var tracks = new ObservedTracks();
        final var cause = EntityNotFoundException.of("Album", album().id().value());
        final var service = new UpdateAlbumService(repository(), access(Uni.createFrom().failure(cause)));
        assertThatThrownBy(() -> service.execute(update(album(), tracks)).await().indefinitely()).isSameAs(cause);
        assertThat(tracks.reads).isZero();
    }

    @Test
    @DisplayName("世代が競合した作品では本文を解析しない")
    void conflictingRevisionDoesNotParseBody() {
        final var existing = album();
        final var tracks = new ObservedTracks();
        final var service = new UpdateAlbumService(repository(), access(
                Uni.createFrom().item(
                        new AlbumRepository.Revisioned(existing, new AlbumRepository.Revision(1)))));
        assertThatThrownBy(() -> service.execute(update(existing, tracks)).await().indefinitely())
                .isInstanceOf(ConflictingEditException.class);
        assertThat(tracks.reads).isZero();
    }

    private static UpdateAlbumInput update(Album album, ObservedTracks tracks) {
        return new UpdateAlbumInput(
                album.id().value(),
                0,
                "updated",
                "2026-01-01",
                "artist",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                tracks,
                null);
    }

    private static Album album() {
        return Album.create(
                AlbumTitle.of("album"),
                BusinessDate.of(
                        2026,
                        1,
                        1),
                ArtistCredit.of("artist"),
                MarkupContent.EMPTY,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static AlbumAccessService access(Uni<AlbumRepository.Revisioned> result) {
        return new AlbumAccessService(null) {
            @Override
            public Uni<AlbumRepository.Revisioned> findExistingAndClaimEditWithRevision(Album.Id id) {
                return result;
            }
        };
    }

    private static AlbumRepository repository() {
        return (AlbumRepository) Proxy.newProxyInstance(
                AlbumRepository.class.getClassLoader(),
                new Class<?>[]{AlbumRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> Uni.createFrom().item(args[0]);
                    case "saveWithRevision" -> Uni.createFrom().item(
                            new AlbumRepository.Revisioned(
                                    (Album) args[0], new AlbumRepository.Revision(1)));
                    default -> throw new AssertionError("Unexpected call on " + proxy.getClass().getSimpleName()
                            + ": " + method.getName());
                });
    }
}
