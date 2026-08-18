package com.abservice.infrastructure.persistence.datasource;

import com.abservice.infrastructure.persistence.entity.TuneTableRecord;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TuneDataSource統合テスト（Phase 9）
 *
 * <p>
 * ドメイン層・Repository層を経由せず、DataSource自身のクエリメソッド（ページング・削除/存在確認）を直接検証します。
 * </p>
 */
@QuarkusTest
class TuneDataSourceTest {

    @Inject
    private TuneDataSource dataSource;

    private static TuneTableRecord newTune(String title) {
        return new TuneTableRecord()
                .setDomainId(UUID.randomUUID().toString())
                .setTitle(title)
                .setTuneKind("TRAD");
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByDomainId(UniAsserter asserter) {
        final var entity = newTune("Find By Domain Id");

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(() -> dataSource.findByDomainId(entity.getDomainId()), found -> {
            assertThat(found).isNotNull();
            assertThat(found.getDomainId()).isEqualTo(entity.getDomainId());
            assertThat(found.getTitle()).isEqualTo("Find By Domain Id");
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldReturnNullWhenDomainIdNotFound(UniAsserter asserter) {
        asserter.assertThat(
                () -> dataSource.findByDomainId(UUID.randomUUID().toString()),
                found -> assertThat(found).isNull());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByIds(UniAsserter asserter) {
        final var entity1 = newTune("Find By Ids 1");
        final var entity2 = newTune("Find By Ids 2");

        asserter.execute(() -> dataSource.persist(entity1));
        asserter.execute(() -> dataSource.persist(entity2));

        asserter.assertThat(
                () -> dataSource.findByIds(List.of(entity1.getDomainId(), entity2.getDomainId())),
                found -> assertThat(found).extracting(TuneTableRecord::getDomainId)
                        .containsExactlyInAnyOrder(entity1.getDomainId(), entity2.getDomainId()));
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByTitle(UniAsserter asserter) {
        final var entity = newTune("Unique Title for DataSource Search");

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(() -> dataSource.findByTitle("Unique Title for DataSource Search"), found -> {
            assertThat(found).isNotEmpty();
            assertThat(found.stream().anyMatch(t -> t.getDomainId().equals(entity.getDomainId()))).isTrue();
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByTuneKind(UniAsserter asserter) {
        final var entity = newTune("Kind Search Tune").setTuneKind("ARRANGEMENT")
                .setOriginalWorkTitle("Original Work");

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(
                () -> dataSource.findByTuneKind("ARRANGEMENT"),
                found -> assertThat(found.stream().anyMatch(t -> t.getDomainId().equals(entity.getDomainId())))
                        .isTrue());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByTuneType(UniAsserter asserter) {
        final var entity = newTune("Type Search Tune").setTuneType("UniqueReelForDataSource");

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(() -> dataSource.findByTuneType("UniqueReelForDataSource"), found -> {
            assertThat(found).isNotEmpty();
            assertThat(found.stream().anyMatch(t -> t.getDomainId().equals(entity.getDomainId()))).isTrue();
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByDefaultKey(UniAsserter asserter) {
        final var entity = newTune("Key Search Tune").setDefaultKey("UniqueDsKey01");

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(() -> dataSource.findByDefaultKey("UniqueDsKey01"), found -> {
            assertThat(found).isNotEmpty();
            assertThat(found.stream().anyMatch(t -> t.getDomainId().equals(entity.getDomainId()))).isTrue();
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldPageResultsWithPagedQuery(UniAsserter asserter) {
        final var entity1 = newTune("Paged Query Tune 1");
        final var entity2 = newTune("Paged Query Tune 2");
        final var entity3 = newTune("Paged Query Tune 3");

        asserter.execute(() -> dataSource.persist(entity1));
        asserter.execute(() -> dataSource.persist(entity2));
        asserter.execute(() -> dataSource.persist(entity3));

        asserter.execute(
                () -> dataSource.pagedQuery(0, 1).count()
                        .invoke(total -> assertThat(total >= 3).isTrue()));

        asserter.execute(
                () -> dataSource.pagedQuery(0, 2).list()
                        .invoke(page -> assertThat(page).hasSizeLessThanOrEqualTo(2)));
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldDeleteByTuneId(UniAsserter asserter) {
        final var entity = newTune("Tune to Delete");

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(
                () -> dataSource.deleteByTuneId(entity.getDomainId()),
                deleted -> assertThat(deleted).isTrue());

        asserter.assertThat(
                () -> dataSource.deleteByTuneId(entity.getDomainId()),
                deleted -> assertThat(deleted).isFalse());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldDeleteByTuneIds(UniAsserter asserter) {
        final var entity1 = newTune("Bulk Delete Tune 1");
        final var entity2 = newTune("Bulk Delete Tune 2");

        asserter.execute(() -> dataSource.persist(entity1));
        asserter.execute(() -> dataSource.persist(entity2));

        asserter.execute(() -> dataSource.deleteByTuneIds(List.of(entity1.getDomainId(), entity2.getDomainId())));

        asserter.assertThat(
                () -> dataSource.findByDomainId(entity1.getDomainId()),
                found -> assertThat(found).isNull());
        asserter.assertThat(
                () -> dataSource.findByDomainId(entity2.getDomainId()),
                found -> assertThat(found).isNull());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldCheckExistsByTuneId(UniAsserter asserter) {
        final var entity = newTune("Existence Check Tune");

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(
                () -> dataSource.existsByTuneId(entity.getDomainId()),
                exists -> assertThat(exists).isTrue());

        asserter.assertThat(
                () -> dataSource.existsByTuneId(UUID.randomUUID().toString()),
                exists -> assertThat(exists).isFalse());
    }
}
