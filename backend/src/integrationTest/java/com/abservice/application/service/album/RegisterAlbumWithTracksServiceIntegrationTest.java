package com.abservice.application.service.album;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.infrastructure.persistence.datasource.AlbumDataSource;
import com.abservice.infrastructure.persistence.repository.AlbumRepositoryImpl;
import com.abservice.test.CleanDatabase;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * 作品を曲目・外部音源ごと1リクエストで登録する（#146・#391）統合テスト
 *
 * <p>
 * REST契約（{@code RegisterAlbumWithTracksRestIntegrationTest}）では確認できない、子の組み立てに失敗したときに
 * 作品自体も永続化されない（トランザクション全体がロールバックされる）ことを永続化層で直接確認する。
 * </p>
 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
class RegisterAlbumWithTracksServiceIntegrationTest {

    private static final String SOUNDCLOUD_URL = "https://soundcloud.com/example/first";

    @Inject
    private RegisterAlbumWithTracksService registerAlbumWithTracksService;

    @Inject
    private AlbumDataSource albumDataSource;

    @Inject
    private AlbumRepositoryImpl albumRepository;

    private static RegisterAlbumWithTracksInput input(
            String title,
            List<TrackInput> tracks,
            List<ExternalAudioInput> externalAudios) {
        return new RegisterAlbumWithTracksInput(
                title,
                "2026-01-01",
                "アーティスト",
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
                externalAudios);
    }

    private static TrackInput track(String title, List<TrackTuneInput> tunes) {
        return new TrackInput(
                null,
                title,
                null,
                null,
                tunes);
    }

    private static TrackTuneInput tune(String tuneTitle, String composerCreditOverride) {
        return new TrackTuneInput(
                tuneTitle,
                composerCreditOverride,
                null,
                null);
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldRegisterAlbumWithTracksInOneTransaction(UniAsserter asserter) {
        asserter.assertThat(
                () -> registerAlbumWithTracksService.execute(
                        input(
                                "ワンリクエスト登録確認アルバム",
                                List.of(
                                        track("1曲目", null),
                                        track("2曲目", null)),
                                null)),
                output -> {
                    assertThat(output.tracks()).hasSize(2);
                    assertThat(output.tracks().get(0).trackNo()).isEqualTo(1);
                    assertThat(output.tracks().get(1).trackNo()).isEqualTo(2);
                });

        asserter.assertThat(
                () -> albumDataSource.findByTitle("ワンリクエスト登録確認アルバム"),
                found -> assertThat(found).hasSize(1));
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldRegisterAlbumWithTrackTunesInOneTransaction(UniAsserter asserter) {
        asserter.assertThat(
                () -> registerAlbumWithTracksService.execute(
                        input(
                                "チューン構成つき登録アルバム",
                                List.of(
                                        track(
                                                "1曲目",
                                                List.of(
                                                        tune("チューン1", "Trad."),
                                                        tune("チューン2", null)))),
                                null)),
                output -> assertThat(output.tracks()).hasSize(1));

        asserter.assertThat(
                () -> albumDataSource.findByTitle("チューン構成つき登録アルバム"),
                found -> assertThat(found).hasSize(1));

        asserter.assertThat(
                () -> albumDataSource.findByTitle("チューン構成つき登録アルバム")
                        .flatMap(found -> albumRepository.findById(Album.Id.of(found.getFirst().getDomainId()))),
                album -> {
                    final var tunes = album.getTracks().getFirst().getTunes();
                    assertThat(tunes).hasSize(2);
                    assertThat(tunes.getFirst().seq()).isEqualTo(1);
                    assertThat(tunes.getFirst().tuneTitle().value()).isEqualTo("チューン1");
                    assertThat(tunes.getFirst().composerCreditOverride().value()).isEqualTo("Trad.");
                    assertThat(tunes.getLast().seq()).isEqualTo(2);
                    assertThat(tunes.getLast().tuneTitle().value()).isEqualTo("チューン2");
                });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldRegisterAlbumWithExternalAudiosInOneTransaction(UniAsserter asserter) {
        asserter.assertThat(
                () -> registerAlbumWithTracksService.execute(
                        input(
                                "外部音源つき登録アルバム",
                                null,
                                List.of(
                                        new ExternalAudioInput(null, SOUNDCLOUD_URL),
                                        new ExternalAudioInput(null, "https://soundcloud.com/example/second")))),
                output -> assertThat(output.albumId()).isNotBlank());

        asserter.assertThat(
                () -> albumDataSource.findByTitle("外部音源つき登録アルバム")
                        .flatMap(found -> albumRepository.findById(Album.Id.of(found.getFirst().getDomainId()))),
                album -> {
                    final var audios = album.getExternalAudiosSortedByDisplayOrder();
                    assertThat(audios).hasSize(2);
                    assertThat(audios.getFirst().displayOrder()).isEqualTo(1);
                    assertThat(audios.getFirst().url().value().value()).isEqualTo(SOUNDCLOUD_URL);
                    assertThat(audios.getLast().displayOrder()).isEqualTo(2);
                });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldNotPersistAlbumWhenExternalAudioUrlIsDuplicated(UniAsserter asserter) {
        asserter.assertFailedWith(
                () -> registerAlbumWithTracksService.execute(
                        input(
                                "ロールバック確認アルバム",
                                List.of(track("1曲目", null)),
                                List.of(
                                        new ExternalAudioInput(null, SOUNDCLOUD_URL),
                                        new ExternalAudioInput(null, SOUNDCLOUD_URL)))),
                BusinessRuleViolationException.class);

        asserter.assertThat(
                () -> albumDataSource.findByTitle("ロールバック確認アルバム"),
                found -> assertThat(found).isEmpty());
    }
}
