package com.abservice.application.service.tune;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.album.Track;
import com.abservice.domain.model.aggregate.album.TrackTune;
import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.album.TrackTitle;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.tune.TuneKind;
import com.abservice.domain.model.vo.tune.TuneTitle;
import com.abservice.infrastructure.persistence.repository.AlbumRepositoryImpl;
import com.abservice.infrastructure.persistence.repository.TuneRepositoryImpl;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * DeleteTuneServiceの参照チェック（#151）の統合テスト
 *
 * <p>
 * トラックへチューン構成を追加するユースケースはまだ無いため、前提データはリポジトリ経由で組み立てる（トラックを
 * 保存してから、再取得したアルバムへ構成を追加する）。参照されているチューンの削除が拒否されること、参照を外せば
 * 削除できること、参照が無ければ従来どおり削除できることを固定する。
 * </p>
 */
@QuarkusTest
class DeleteTuneServiceIntegrationTest {

    @Inject
    private DeleteTuneService deleteTuneService;

    @Inject
    private TuneRepositoryImpl tuneRepository;

    @Inject
    private AlbumRepositoryImpl albumRepository;

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldDeleteTuneWhenNoTrackReferencesIt(UniAsserter asserter) {
        final var tune = newTune("参照なしチューン");
        asserter.execute(() -> tuneRepository.save(tune));

        asserter.execute(() -> deleteTuneService.execute(new DeleteTuneInput(tune.id().value())));

        asserter.assertThat(
                () -> tuneRepository.findById(tune.id()),
                found -> assertThat(found).isNull());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldRejectDeletingTuneReferencedByTrack(UniAsserter asserter) {
        final var tune = newTune("参照ありチューン");
        final var album = newAlbumWithTrack("参照ありチューンのアルバム");
        asserter.execute(() -> tuneRepository.save(tune));
        asserter.execute(() -> albumRepository.save(album));
        asserter.execute(() -> referenceTune(album, tune));

        asserter.assertFailedWith(
                () -> deleteTuneService.execute(new DeleteTuneInput(tune.id().value())),
                BusinessRuleViolationException.class);

        asserter.assertThat(
                () -> tuneRepository.findById(tune.id()),
                found -> assertThat(found).isNotNull());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldDeleteTuneAfterReferenceIsRemoved(UniAsserter asserter) {
        final var tune = newTune("参照を外すチューン");
        final var album = newAlbumWithTrack("参照を外すチューンのアルバム");
        asserter.execute(() -> tuneRepository.save(tune));
        asserter.execute(() -> albumRepository.save(album));
        asserter.execute(() -> referenceTune(album, tune));
        asserter.execute(() -> dereferenceTune(album));

        asserter.execute(() -> deleteTuneService.execute(new DeleteTuneInput(tune.id().value())));

        asserter.assertThat(
                () -> tuneRepository.findById(tune.id()),
                found -> assertThat(found).isNull());
    }

    private Uni<Album> referenceTune(Album album, Tune tune) {
        return albumRepository.findById(album.id())
                .map(
                        saved -> saved.updateTrack(
                                saved.tracks().getFirst()
                                        .addTune(
                                                TrackTune.create(
                                                        1,
                                                        tune.id(),
                                                        null,
                                                        null,
                                                        null))))
                .flatMap(albumRepository::save);
    }

    private Uni<Album> dereferenceTune(Album album) {
        return albumRepository.findById(album.id())
                .map(saved -> saved.updateTrack(saved.tracks().getFirst().removeTune(1)))
                .flatMap(albumRepository::save);
    }

    private static Tune newTune(String title) {
        return Tune.create(
                new TuneTitle(title),
                TuneKind.ORIGINAL,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static Album newAlbumWithTrack(String title) {
        return newAlbum(title)
                .addTrack(
                        Track.create(
                                1,
                                new TrackTitle("参照するトラック"),
                                null,
                                null));
    }

    private static Album newAlbum(String title) {
        return Album.create(
                new AlbumTitle(title),
                BusinessDate.of(
                        LocalDate.of(
                                2026,
                                1,
                                1)),
                ArtistCredit.of("チューン参照テストアーティスト"),
                null,
                null,
                null,
                null);
    }
}
