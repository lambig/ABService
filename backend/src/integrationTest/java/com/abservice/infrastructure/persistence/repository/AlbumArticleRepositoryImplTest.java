package com.abservice.infrastructure.persistence.repository;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.albumarticle.AlbumAcquisitionChannel;
import com.abservice.domain.model.aggregate.albumarticle.AlbumArticle;
import com.abservice.domain.model.aggregate.albumarticle.AlbumDistribution;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.album.ChannelType;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.Price;
import com.abservice.domain.model.vo.common.Url;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AlbumArticleRepositoryImpl統合テスト（#40: 頒布情報、#41: 入手経路のラウンドトリップ）
 */
@QuarkusTest
class AlbumArticleRepositoryImplTest {

    @Inject
    private AlbumRepositoryImpl albumRepository;

    @Inject
    private AlbumArticleRepositoryImpl repository;

    private Album newAlbum(String title) {
        final var releaseDate = LocalDate.of(
                2024,
                1,
                1);
        return Album.create(
                new AlbumTitle(title),
                BusinessDate.of(releaseDate),
                ArtistCredit.of("Test Artist", "test-artist"),
                null,
                null,
                null);
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldRestoreDistributionOnRoundTrip(UniAsserter asserter) {
        final var album = newAlbum("Distribution Album");
        final var distribution = AlbumDistribution.create(
                Price.of(3000),
                Price.of(1500),
                Url.of("https://example.com/demo"),
                "Demo note");
        final var article = AlbumArticle.create(
                album.id(),
                "Long intro",
                "Short intro",
                "East A-01",
                null,
                distribution);

        asserter.execute(() -> albumRepository.save(album));
        asserter.execute(() -> repository.save(article));

        asserter.assertThat(
                () -> repository.findByAlbumId(album.id()),
                found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.distribution()).isNotNull();
                    assertThat(found.distribution().getPhysicalPrice().amount()).isEqualTo(3000);
                    assertThat(found.distribution().getDownloadPrice().amount()).isEqualTo(1500);
                    assertThat(found.distribution().getDemoUrl().value()).isEqualTo("https://example.com/demo");
                    assertThat(found.distribution().getNote()).isEqualTo("Demo note");
                });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldHaveNullDistributionWhenNotSet(UniAsserter asserter) {
        final var album = newAlbum("No Distribution Album");
        final var article = AlbumArticle.create(
                album.id(),
                null,
                null,
                null,
                null,
                null);

        asserter.execute(() -> albumRepository.save(album));
        asserter.execute(() -> repository.save(article));

        asserter.assertThat(
                () -> repository.findByAlbumId(album.id()),
                found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.distribution()).isNull();
                    assertThat(found.getAcquisitionChannels()).isEmpty();
                });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldUpdateAndRemoveDistributionOnResave(UniAsserter asserter) {
        final var album = newAlbum("Update Distribution Album");
        final var originalDistribution = AlbumDistribution.create(
                Price.of(1000),
                null,
                null,
                "Original note");
        final var original = AlbumArticle.create(
                album.id(),
                null,
                null,
                null,
                null,
                originalDistribution);

        asserter.execute(() -> albumRepository.save(album));
        asserter.execute(() -> repository.save(original));

        final var updatedDistribution = AlbumDistribution.create(
                Price.of(2000),
                Price.of(500),
                null,
                "Updated note");
        final var updated = AlbumArticle.reconstruct(
                album.id(),
                null,
                null,
                null,
                null,
                updatedDistribution,
                List.of());

        asserter.execute(() -> repository.save(updated));

        asserter.assertThat(
                () -> repository.findByAlbumId(album.id()),
                found -> {
                    assertThat(found.distribution().getPhysicalPrice().amount()).isEqualTo(2000);
                    assertThat(found.distribution().getDownloadPrice().amount()).isEqualTo(500);
                    assertThat(found.distribution().getNote()).isEqualTo("Updated note");
                });

        final var cleared = AlbumArticle.reconstruct(
                album.id(),
                null,
                null,
                null,
                null,
                null,
                List.of());
        asserter.execute(() -> repository.save(cleared));

        asserter.assertThat(
                () -> repository.findByAlbumId(album.id()),
                found -> assertThat(found.distribution()).isNull());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldRestoreAcquisitionChannelsOnRoundTrip(UniAsserter asserter) {
        final var album = newAlbum("Channels Album");
        final var channel1 = AlbumAcquisitionChannel.create(
                ChannelType.EVENT,
                "Event Table A-01",
                null,
                null);
        final var channel2 = AlbumAcquisitionChannel.create(
                ChannelType.DL_SITE,
                "BOOTH",
                Url.of("https://booth.pm/example"),
                "DL note");
        final var article = AlbumArticle.create(
                album.id(),
                null,
                null,
                null,
                null,
                null)
                .addAcquisitionChannel(channel1)
                .addAcquisitionChannel(channel2);

        asserter.execute(() -> albumRepository.save(album));
        asserter.execute(() -> repository.save(article));

        asserter.assertThat(
                () -> repository.findByAlbumId(album.id()),
                found -> {
                    assertThat(found.getAcquisitionChannels()).hasSize(2);
                    assertThat(found.getAcquisitionChannels())
                            .anySatisfy(c -> assertThat(c.getName()).isEqualTo("Event Table A-01"))
                            .anySatisfy(c -> {
                                assertThat(c.getName()).isEqualTo("BOOTH");
                                assertThat(c.getUrl().value()).isEqualTo("https://booth.pm/example");
                                assertThat(c.getNote()).isEqualTo("DL note");
                            });
                });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldAddUpdateAndRemoveAcquisitionChannelsOnResave(UniAsserter asserter) {
        final var album = newAlbum("Reconcile Channels Album");
        final var keepChannel = AlbumAcquisitionChannel.create(
                ChannelType.ONLINE_SHOP,
                "Melonbooks",
                null,
                null);
        final var removeChannel = AlbumAcquisitionChannel.create(
                ChannelType.EVENT,
                "Old Event",
                null,
                null);
        final var original = AlbumArticle.create(
                album.id(),
                null,
                null,
                null,
                null,
                null)
                .addAcquisitionChannel(keepChannel)
                .addAcquisitionChannel(removeChannel);

        asserter.execute(() -> albumRepository.save(album));
        asserter.execute(() -> repository.save(original));

        asserter.execute(
                () -> repository.findByAlbumId(album.id())
                        .flatMap(
                                loaded -> {
                                    final var updatedKeep = keepChannel.changeName("Melonbooks Updated");
                                    final var newChannel = AlbumAcquisitionChannel.create(
                                            ChannelType.STREAMING,
                                            "Spotify",
                                            null,
                                            null);
                                    final var next = loaded.removeAcquisitionChannel(removeChannel.id())
                                            .updateAcquisitionChannel(updatedKeep)
                                            .addAcquisitionChannel(newChannel);
                                    return repository.save(next);
                                }));

        asserter.assertThat(
                () -> repository.findByAlbumId(album.id()),
                found -> {
                    assertThat(found.getAcquisitionChannels()).hasSize(2);
                    assertThat(found.getAcquisitionChannels())
                            .extracting(AlbumAcquisitionChannel::getName)
                            .containsExactlyInAnyOrder("Melonbooks Updated", "Spotify");
                });
    }
}
