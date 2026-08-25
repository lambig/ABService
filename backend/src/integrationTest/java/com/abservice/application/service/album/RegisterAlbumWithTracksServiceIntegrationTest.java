package com.abservice.application.service.album;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.infrastructure.persistence.datasource.AlbumDataSource;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * アルバムとその初期トラック一覧のワンリクエスト登録（#146・専用ユースケース）の統合テスト
 *
 * <p>
 * REST契約（{@code RegisterAlbumWithTracksRestIntegrationTest}）では確認できない、トラック追加失敗時に
 * アルバム自体も永続化されない（トランザクション全体がロールバックされる）ことを永続化層で直接確認する。
 * </p>
 */
@QuarkusTest
class RegisterAlbumWithTracksServiceIntegrationTest {

    @Inject
    private RegisterAlbumWithTracksService registerAlbumWithTracksService;

    @Inject
    private AlbumDataSource albumDataSource;

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldRegisterAlbumWithTracksInOneTransaction(UniAsserter asserter) {
        asserter.assertThat(
                () -> registerAlbumWithTracksService.execute(
                        new RegisterAlbumWithTracksInput(
                                "ワンリクエスト登録確認アルバム",
                                "2026-01-01",
                                "アーティスト",
                                null,
                                null,
                                null,
                                null,
                                null,
                                List.of(
                                        new RegisterAlbumWithTracksInput.TrackInput(
                                                1,
                                                "1曲目",
                                                null,
                                                null,
                                                null,
                                                null,
                                                null),
                                        new RegisterAlbumWithTracksInput.TrackInput(
                                                2,
                                                "2曲目",
                                                null,
                                                null,
                                                null,
                                                null,
                                                null)))),
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
    void shouldNotPersistAlbumWhenTrackAdditionFails(UniAsserter asserter) {
        asserter.assertFailedWith(
                () -> registerAlbumWithTracksService.execute(
                        new RegisterAlbumWithTracksInput(
                                "ロールバック確認アルバム",
                                "2026-01-01",
                                "アーティスト",
                                null,
                                null,
                                null,
                                null,
                                null,
                                List.of(
                                        new RegisterAlbumWithTracksInput.TrackInput(
                                                1,
                                                "1曲目",
                                                null,
                                                null,
                                                null,
                                                null,
                                                null),
                                        new RegisterAlbumWithTracksInput.TrackInput(
                                                1,
                                                "重複する1曲目",
                                                null,
                                                null,
                                                null,
                                                null,
                                                null)))),
                BusinessRuleViolationException.class);

        asserter.assertThat(
                () -> albumDataSource.findByTitle("ロールバック確認アルバム"),
                found -> assertThat(found).isEmpty());
    }
}
