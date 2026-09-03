package com.abservice.infrastructure.persistence.repository;

import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.domain.model.vo.common.Credit;
import com.abservice.domain.model.vo.tune.TuneKind;
import com.abservice.domain.model.vo.tune.TuneTitle;
import com.abservice.test.CleanDatabase;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TuneRepositoryImpl統合テスト
 *
 * <p>
 * Quarkus + Hibernate
 * Reactiveの統合テストでは、{@link RunOnVertxContext}と{@link UniAsserter}を使用します。
 * これにより、リアクティブなデータベース操作が適切なVertxコンテキスト内で実行されます。
 * </p>
 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
class TuneRepositoryImplTest {

    @Inject
    private TuneRepositoryImpl repository;

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldSaveAndFindTune(UniAsserter asserter) {
        final var tune = Tune.create(
                TuneTitle.of("Test Tune"),
                TuneKind.TRAD,
                Credit.of("Trad."),
                null,
                null,
                null,
                "Jig",
                "D",
                120);

        asserter.assertThat(() -> repository.save(tune), saved -> {
            assertThat(saved).isNotNull();
            assertThat(saved.id()).isEqualTo(tune.id());
            assertThat(saved.title().value()).isEqualTo("Test Tune");
            assertThat(saved.tuneKind()).isEqualTo(TuneKind.TRAD);
            assertThat(saved.defaultComposerCredit().value()).isEqualTo("Trad.");
            assertThat(saved.tuneType()).isEqualTo("Jig");
            assertThat(saved.defaultKey()).isEqualTo("D");
            assertThat(saved.defaultTempo()).isEqualTo(120);
        });

        asserter.assertThat(() -> repository.findById(tune.id()), found -> {
            assertThat(found).isNotNull();
            assertThat(found.id()).isEqualTo(tune.id());
            assertThat(found.title().value()).isEqualTo("Test Tune");
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldSaveArrangementTuneWithOriginalWorkInfo(UniAsserter asserter) {
        final var tune = Tune.create(
                TuneTitle.of("Arranged Tune"),
                TuneKind.ARRANGEMENT,
                Credit.of("Original Composer"),
                Credit.of("Arranger"),
                "Original Work Title",
                "Original Artist",
                null,
                null,
                null);

        asserter.assertThat(() -> repository.save(tune), saved -> {
            assertThat(saved.tuneKind()).isEqualTo(TuneKind.ARRANGEMENT);
            assertThat(saved.defaultArrangerCredit().value()).isEqualTo("Arranger");
            assertThat(saved.originalWorkTitle()).isEqualTo("Original Work Title");
            assertThat(saved.originalWorkCredit()).isEqualTo("Original Artist");
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldUpdateExistingTune(UniAsserter asserter) {
        final var tune = Tune.create(
                TuneTitle.of("Original Title"),
                TuneKind.TRAD,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        asserter.assertThat(
                () -> repository.save(tune),
                saved -> assertThat(saved.title().value()).isEqualTo("Original Title"));

        final var updated = Tune.reconstruct(
                tune.id(),
                TuneTitle.of("Updated Title"),
                TuneKind.ORIGINAL,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        asserter.assertThat(() -> repository.save(updated), result -> {
            assertThat(result.id()).isEqualTo(tune.id());
            assertThat(result.title().value()).isEqualTo("Updated Title");
            assertThat(result.tuneKind()).isEqualTo(TuneKind.ORIGINAL);
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldDeleteTune(UniAsserter asserter) {
        final var tune = Tune.create(
                TuneTitle.of("Tune to Delete"),
                TuneKind.TRAD,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        asserter.assertThat(() -> repository.save(tune), saved -> assertThat(saved).isNotNull());

        asserter.execute(() -> repository.deleteById(tune.id()));

        asserter.assertThat(() -> repository.findById(tune.id()), found -> assertThat(found).isNull());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldCheckExistence(UniAsserter asserter) {
        final var tune = Tune.create(
                TuneTitle.of("Existing Tune"),
                TuneKind.TRAD,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        asserter.execute(() -> repository.save(tune));

        asserter.assertThat(() -> repository.existsById(tune.id()), exists -> assertThat(exists).isTrue());

        final var nonExistentId = Tune.Id.of("01234567-89ab-7def-0123-456789abcdef");
        asserter.assertThat(() -> repository.existsById(nonExistentId), exists -> assertThat(exists).isFalse());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldCountTunes(UniAsserter asserter) {
        final var tune1 = Tune.create(
                TuneTitle.of("Count Tune 1"),
                TuneKind.TRAD,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        final var tune2 = Tune.create(
                TuneTitle.of("Count Tune 2"),
                TuneKind.TRAD,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        asserter.execute(() -> repository.save(tune1));
        asserter.execute(() -> repository.save(tune2));

        asserter.assertThat(() -> repository.count(), count -> assertThat(count >= 2).isTrue());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByTitle(UniAsserter asserter) {
        final var title = TuneTitle.of("Unique Title for Search");
        final var tune = Tune.create(
                title,
                TuneKind.TRAD,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        asserter.execute(() -> repository.save(tune));

        asserter.assertThat(() -> repository.findByTitle(title), found -> {
            assertThat(found.size() >= 1).isTrue();
            assertThat(found.stream().anyMatch(t -> t.title().value().equals("Unique Title for Search"))).isTrue();
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByTuneKind(UniAsserter asserter) {
        final var tune = Tune.create(
                TuneTitle.of("Kind Search Tune"),
                TuneKind.ARRANGEMENT,
                null,
                null,
                "Original",
                null,
                null,
                null,
                null);

        asserter.execute(() -> repository.save(tune));

        asserter.assertThat(() -> repository.findByTuneKind(TuneKind.ARRANGEMENT), found -> {
            assertThat(found.stream().anyMatch(t -> t.id().equals(tune.id()))).isTrue();
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByTuneType(UniAsserter asserter) {
        final var tune = Tune.create(
                TuneTitle.of("Type Search Tune"),
                TuneKind.TRAD,
                null,
                null,
                null,
                null,
                "UniqueReel",
                null,
                null);

        asserter.execute(() -> repository.save(tune));

        asserter.assertThat(() -> repository.findByTuneType("UniqueReel"), found -> {
            assertThat(found).isNotEmpty();
            assertThat(found.stream().anyMatch(t -> t.id().equals(tune.id()))).isTrue();
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByDefaultKey(UniAsserter asserter) {
        final var tune = Tune.create(
                TuneTitle.of("Key Search Tune"),
                TuneKind.TRAD,
                null,
                null,
                null,
                null,
                null,
                "UniqueKey",
                null);

        asserter.execute(() -> repository.save(tune));

        asserter.assertThat(() -> repository.findByDefaultKey("UniqueKey"), found -> {
            assertThat(found).isNotEmpty();
            assertThat(found.stream().anyMatch(t -> t.id().equals(tune.id()))).isTrue();
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldHandleNullInputs(UniAsserter asserter) {
        assertThatThrownBy(() -> repository.save(null)).isInstanceOf(NullPointerException.class);

        asserter.assertThat(() -> repository.findById(null), found -> assertThat(found).isNull());

        asserter.assertThat(() -> repository.existsById(null), exists -> assertThat(exists).isFalse());

        asserter.execute(() -> repository.deleteById(null));
    }
}
