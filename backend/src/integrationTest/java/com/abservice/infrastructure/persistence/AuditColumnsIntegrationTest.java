package com.abservice.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.infrastructure.persistence.datasource.AlbumDataSource;
import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import com.abservice.infrastructure.persistence.repository.AlbumRepositoryImpl;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 共通監査列の現在の契約（{@code docs/DECISIONS.md} 5）の統合テスト
 *
 * <p>
 * 日時2列はアプリケーションが設定し、actor 4列は未特定のまま運用する。actor を埋める判断が出た時点で本テストは
 * 書き換える対象になる（列が黙って部分的に埋まり始めることを防ぐために固定している）。
 * </p>
 */
@QuarkusTest
class AuditColumnsIntegrationTest {

    @Inject
    private AlbumRepositoryImpl albumRepository;

    @Inject
    private AlbumDataSource albumDataSource;

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldSetTimestampsAndLeaveActorColumnsUnspecified(UniAsserter asserter) {
        final var album = newAlbum("監査列テストアルバム");

        asserter.execute(() -> albumRepository.save(album));

        asserter.assertThat(
                () -> albumDataSource.findByIdWithTracks(album.id().value()),
                saved -> {
                    assertThat(saved.getCreatedAt()).as("作成日時は設定される").isNotNull();
                    assertThat(saved.getUpdatedAt()).as("更新日時は設定される").isNotNull();
                    assertThat(saved.getVersion()).as("楽観ロックの版は0から始まる").isZero();
                    assertActorColumnsUnspecified(saved);
                });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldKeepCreatedAtAndActorColumnsOnUpdate(UniAsserter asserter) {
        final var album = newAlbum("監査列更新テストアルバム");

        asserter.execute(() -> albumRepository.save(album));

        final var captured = new AlbumTableRecord[1];
        asserter.assertThat(
                () -> albumDataSource.findByIdWithTracks(album.id().value()),
                saved -> captured[0] = saved);

        asserter.execute(() -> albumRepository.save(album.changeTitle(new AlbumTitle("監査列更新テスト後"))));

        asserter.assertThat(
                () -> albumDataSource.findByIdWithTracks(album.id().value()),
                updated -> {
                    assertThat(updated.getCreatedAt()).as("作成日時は更新で変わらない")
                            .isEqualTo(captured[0].getCreatedAt());
                    assertThat(updated.getVersion()).as("更新で版が進む").isPositive();
                    assertActorColumnsUnspecified(updated);
                });
    }

    private static void assertActorColumnsUnspecified(AlbumTableRecord record) {
        assertThat(record.getCreatedByService()).as("actor列は未特定のまま").isNull();
        assertThat(record.getUpdatedByService()).as("actor列は未特定のまま").isNull();
        assertThat(record.getCreatedByUser()).as("actor列は未特定のまま").isNull();
        assertThat(record.getUpdatedByUser()).as("actor列は未特定のまま").isNull();
    }

    private static Album newAlbum(String title) {
        return Album.create(
                new AlbumTitle(title),
                BusinessDate.of(
                        LocalDate.of(
                                2026,
                                1,
                                1)),
                ArtistCredit.of("監査列テストアーティスト"),
                null,
                null,
                null,
                null);
    }
}
