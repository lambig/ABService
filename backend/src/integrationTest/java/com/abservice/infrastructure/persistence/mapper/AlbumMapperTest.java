package com.abservice.infrastructure.persistence.mapper;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.album.Track;
import com.abservice.domain.model.aggregate.album.TrackTune;
import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.album.CatalogNumber;
import com.abservice.domain.model.vo.album.Isdn;
import com.abservice.domain.model.vo.album.TrackTitle;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.AssetKey;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.domain.model.vo.common.Credit;
import com.abservice.domain.model.vo.common.EventReleasedAt;
import com.abservice.domain.model.vo.common.MarkupContent;
import com.abservice.domain.model.vo.common.MarkupFormat;
import com.abservice.domain.model.vo.common.Url;
import com.abservice.infrastructure.persistence.datasource.AlbumDataSource;
import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import com.abservice.infrastructure.persistence.entity.TrackTableRecord;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AlbumMapper統合テスト（Phase 8）
 *
 * <p>
 * 実DBへ永続化・再取得したエンティティに対して {@link AlbumMapper} を直接呼び出し、
 * Entity⇔Domain変換の正しさを検証します。
 * </p>
 */
@QuarkusTest
class AlbumMapperTest {

    @Inject
    private AlbumDataSource dataSource;

    private static AlbumTableRecord newAlbum(String title) {
        return new AlbumTableRecord()
                .setDomainId(UUID.randomUUID().toString())
                .setTitle(title)
                .setReleaseDate(
                        LocalDate.of(
                                2024,
                                1,
                                1))
                .setArtistDisplayName("Test Artist")
                .setArtistSortKey("test-artist");
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldMapEntityToDomainWithAllFields(UniAsserter asserter) {
        final var album = newAlbum("Full Fields Album")
                .setDescription("全項目マッピング確認用の概要説明")
                .setDescriptionFormat("MARKDOWN")
                .setCatalogNumber("MAPPER-CAT-001")
                .setIsdn("2784702901978")
                .setEventName("Comiket 104")
                .setEventDate(
                        LocalDate.of(
                                2024,
                                12,
                                31))
                .setEventPlace("Tokyo Big Sight")
                .setEventSpaceNumber("East A-01")
                .setEventNote("Event note");
        album.getTracks()
                .add(
                        new TrackTableRecord()
                                .setDomainId(UUID.randomUUID().toString())
                                .setAlbum(album)
                                .setTrackNo(1)
                                .setTitle("Track One")
                                .setArtistDisplayName("Track Artist")
                                .setArtistSortKey("track-artist"));

        asserter.execute(() -> dataSource.persistAlbumWithRelations(album));

        asserter.assertThat(() -> dataSource.findByIdWithTracks(album.getDomainId()), found -> {
            final var domain = AlbumMapper.toDomain(found);

            assertThat(domain.id().value()).isEqualTo(album.getDomainId());
            assertThat(domain.title().value()).isEqualTo("Full Fields Album");
            assertThat(domain.releaseDate().asLocalDate()).isEqualTo(
                    LocalDate.of(
                            2024,
                            1,
                            1));
            assertThat(domain.artistCredit().displayName().value()).isEqualTo("Test Artist");
            assertThat(domain.artistCredit().sortKey()).isEqualTo("test-artist");
            assertThat(domain.description().content()).isEqualTo("全項目マッピング確認用の概要説明");
            assertThat(domain.description().format()).isEqualTo(MarkupFormat.MARKDOWN);
            assertThat(domain.catalogNumber().value()).isEqualTo("MAPPER-CAT-001");
            assertThat(domain.isdn().value()).isEqualTo("2784702901978");
            assertThat(domain.eventReleasedAt()).isNotNull();
            assertThat(domain.eventReleasedAt().name().value()).isEqualTo("Comiket 104");
            assertThat(domain.eventReleasedAt().place()).isEqualTo("Tokyo Big Sight");
            assertThat(domain.eventReleasedAt().spaceNumber()).isEqualTo("East A-01");
            assertThat(domain.eventReleasedAt().note()).isEqualTo("Event note");
            assertThat(domain.tracks()).hasSize(1);
            final var track = domain.tracks().get(0);
            assertThat(track.title().value()).isEqualTo("Track One");
            assertThat(track.trackNo()).isEqualTo(1);
            assertThat(track.artistCredit().displayName().value()).isEqualTo("Track Artist");
            assertThat(track.tunes()).isEmpty();
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldMapEntityToDomainWithMinimalFields(UniAsserter asserter) {
        final var album = newAlbum("Minimal Fields Album").setArtistSortKey(null);

        asserter.execute(() -> dataSource.persistAlbumWithRelations(album));

        asserter.assertThat(() -> dataSource.findByIdWithTracks(album.getDomainId()), found -> {
            final var domain = AlbumMapper.toDomain(found);

            assertThat(domain.catalogNumber()).isNull();
            assertThat(domain.isdn()).isNull();
            assertThat(domain.eventReleasedAt()).isNull();
            assertThat(domain.tracks()).isEmpty();
            assertThat(domain.isPublished()).isFalse();
            assertThat(domain.publication().publishedAt()).isEmpty();
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldMapEntityToDomainWithPublishedAt(UniAsserter asserter) {
        final var publishedAt = Instant.parse("2024-06-01T00:00:00Z");
        final var album = newAlbum("Published Album").setPublishedAt(publishedAt);

        asserter.execute(() -> dataSource.persistAlbumWithRelations(album));

        asserter.assertThat(() -> dataSource.findByIdWithTracks(album.getDomainId()), found -> {
            final var domain = AlbumMapper.toDomain(found);

            assertThat(domain.isPublished()).isTrue();
            assertThat(domain.publication().publishedAt()).contains(BusinessDateTime.of(publishedAt));
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldMapDomainToEntityAndPersist(UniAsserter asserter) {
        final var album = Album.create(
                AlbumTitle.of("Domain Mapped Album"),
                BusinessDate.of(
                        2025,
                        3,
                        3),
                ArtistCredit.of("Mapped Artist", "mapped-artist"),
                MarkupContent.markdown("マッピング確認用の概要説明"),
                EventReleasedAt.of(
                        "Mapped Event",
                        BusinessDate.of(
                                2025,
                                3,
                                3),
                        "Mapped Place",
                        "West B-02",
                        "Mapped Note"),
                CatalogNumber.of("MAPPED-CAT-001"),
                Isdn.of("2794123456780"),
                AssetKey.of("01a0233d-d25a-7c3b-924f-236ee154fecc.png"))
                .addTrack(
                        Track.create(
                                1,
                                TrackTitle.of("Mapped Track"),
                                ArtistCredit.of("Mapped Track Artist")));

        final var entity = AlbumMapper.toEntity(album);

        assertThat(entity.getDomainId()).isEqualTo(album.id().value());
        assertThat(entity.getTitle()).isEqualTo("Domain Mapped Album");
        assertThat(entity.getCatalogNumber()).isEqualTo("MAPPED-CAT-001");
        assertThat(entity.getIsdn()).isEqualTo("2794123456780");
        assertThat(entity.getEventName()).isEqualTo("Mapped Event");
        assertThat(entity.getDescription()).isEqualTo("マッピング確認用の概要説明");
        assertThat(entity.getDescriptionFormat()).isEqualTo("MARKDOWN");
        assertThat(entity.getPublishedAt()).isNull();
        assertThat(entity.getTracks()).hasSize(1);
        assertThat(entity.getTracks().get(0).getTitle()).isEqualTo("Mapped Track");
        assertThat(entity.getTracks().get(0).getAlbum()).isSameAs(entity);

        asserter.execute(() -> dataSource.persistAlbumWithRelations(entity));

        asserter.assertThat(() -> dataSource.findByIdWithTracks(album.id().value()), found -> {
            assertThat(found.getTitle()).isEqualTo("Domain Mapped Album");
            assertThat(found.getTracks()).hasSize(1);
            assertThat(found.getTracks().get(0).getTitle()).isEqualTo("Mapped Track");
        });
    }

    @Test
    void shouldMapPublishedDomainToEntity() {
        final var publishedAt = BusinessDateTime.of(Instant.parse("2024-06-01T00:00:00Z"));
        final var album = Album.create(
                AlbumTitle.of("Published Domain Album"),
                BusinessDate.of(
                        2025,
                        3,
                        3),
                ArtistCredit.of("Mapped Artist"),
                MarkupContent.EMPTY,
                null,
                null,
                null,
                null)
                .publish(publishedAt);

        final var entity = AlbumMapper.toEntity(album);

        assertThat(entity.getPublishedAt()).isEqualTo(publishedAt.value());
    }

    @Test
    void shouldClearDescriptionColumnWhenChangedToEmpty() {
        final var album = Album.create(
                AlbumTitle.of("Description Cleared Album"),
                BusinessDate.of(
                        2025,
                        3,
                        3),
                ArtistCredit.of("Mapped Artist"),
                MarkupContent.markdown("あとで消される概要説明"),
                null,
                null,
                null,
                null)
                .changeDescription(MarkupContent.EMPTY);

        final var entity = AlbumMapper.toEntity(album);

        assertThat(entity.getDescription()).isNull();
        assertThat(entity.getDescriptionFormat()).isEqualTo("PLAIN_TEXT");
    }

    @Test
    void shouldMapUnpublishedDomainToEntityWithNullPublishedAt() {
        final var album = Album.create(
                AlbumTitle.of("Unpublished Domain Album"),
                BusinessDate.of(
                        2025,
                        3,
                        3),
                ArtistCredit.of("Mapped Artist"),
                MarkupContent.EMPTY,
                null,
                null,
                null,
                null)
                .publish(BusinessDateTime.of(Instant.parse("2024-06-01T00:00:00Z")))
                .unpublish();

        final var entity = AlbumMapper.toEntity(album);

        assertThat(entity.getPublishedAt()).isNull();
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldMapTrackTuneRoundTrip(UniAsserter asserter) {
        final var album = newAlbum("TrackTune Mapper Album");
        album.getTracks()
                .add(
                        new TrackTableRecord()
                                .setDomainId(UUID.randomUUID().toString())
                                .setAlbum(album)
                                .setTrackNo(1)
                                .setTitle("TrackTune Host Track"));

        asserter.execute(() -> dataSource.persistAlbumWithRelations(album));

        asserter.execute(
                () -> dataSource.findByIdWithTracks(album.getDomainId())
                        .invoke(
                                found -> {
                                    final var trackEntity = found.getTracks().get(0);
                                    final var tuneId = Tune.Id.generate();
                                    final var trackTune = TrackTune.create(
                                            1,
                                            tuneId,
                                            Credit.of("Composer Override"),
                                            Credit.of("Arranger Override"),
                                            Url.of("https://example.com/tune"));

                                    final var trackTuneEntity = AlbumMapper.trackTuneToEntity(
                                            trackTune,
                                            trackEntity);

                                    assertThat(trackTuneEntity.getId().getTrackId())
                                            .isEqualTo(trackEntity.getTrackId());
                                    assertThat(trackTuneEntity.getId().getSeq()).isEqualTo(1);
                                    assertThat(trackTuneEntity.getTuneId()).isEqualTo(tuneId.value());
                                    assertThat(trackTuneEntity.getComposerCreditOverride())
                                            .isEqualTo("Composer Override");
                                    assertThat(trackTuneEntity.getArrangerCreditOverride())
                                            .isEqualTo("Arranger Override");
                                    assertThat(trackTuneEntity.getLinkUrl())
                                            .isEqualTo("https://example.com/tune");

                                    /*
                                     * trackTuneToDomainはprivateのため、公開APIのtoDomain経由で
                                     * 間接的に検証する。trackEntityは永続化コンテキストに管理されており
                                     * orphanRemoval下でtrackTunesの参照差し替えが許されないため、 未管理の複製エンティティを組み立てて渡す。
                                     */
                                    final var trackCopy = new TrackTableRecord()
                                            .setDomainId(trackEntity.getDomainId())
                                            .setTrackNo(trackEntity.getTrackNo())
                                            .setTitle(trackEntity.getTitle())
                                            .setTrackTunes(List.of(trackTuneEntity));
                                    final var albumCopy = new AlbumTableRecord()
                                            .setDomainId(found.getDomainId())
                                            .setTitle(found.getTitle())
                                            .setReleaseDate(found.getReleaseDate())
                                            .setArtistDisplayName(found.getArtistDisplayName())
                                            .setTracks(List.of(trackCopy));
                                    final var roundTrippedAlbum = AlbumMapper.toDomain(albumCopy);
                                    final var roundTripped = roundTrippedAlbum.tracks().get(0).tunes().get(0);
                                    assertThat(roundTripped.seq()).isEqualTo(1);
                                    assertThat(roundTripped.tuneId()).isEqualTo(tuneId);
                                    assertThat(roundTripped.composerCreditOverride().value())
                                            .isEqualTo("Composer Override");
                                    assertThat(roundTripped.arrangerCreditOverride().value())
                                            .isEqualTo("Arranger Override");
                                    assertThat(roundTripped.linkUrl().value())
                                            .isEqualTo("https://example.com/tune");
                                }));
    }
}
