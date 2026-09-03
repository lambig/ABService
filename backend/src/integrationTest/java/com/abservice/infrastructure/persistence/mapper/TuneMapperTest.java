package com.abservice.infrastructure.persistence.mapper;

import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.domain.model.vo.common.Credit;
import com.abservice.domain.model.vo.tune.TuneKind;
import com.abservice.domain.model.vo.tune.TuneTitle;
import com.abservice.infrastructure.persistence.datasource.TuneDataSource;
import com.abservice.infrastructure.persistence.entity.TuneTableRecord;
import com.abservice.test.CleanDatabase;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TuneMapper統合テスト（Phase 8）
 *
 * <p>
 * 実DBへ永続化・再取得したエンティティに対して {@link TuneMapper} を直接呼び出し、
 * Entity⇔Domain変換の正しさを検証します。
 * </p>
 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
class TuneMapperTest {

    @Inject
    private TuneDataSource dataSource;

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldMapEntityToDomainWithAllFields(UniAsserter asserter) {
        final var entity = new TuneTableRecord()
                .setDomainId(UUID.randomUUID().toString())
                .setTitle("Arranged Tune")
                .setTuneKind("ARRANGEMENT")
                .setDefaultComposerCredit("Original Composer")
                .setDefaultArrangerCredit("Arranger")
                .setOriginalWorkTitle("Original Work")
                .setOriginalWorkCredit("Original Artist")
                .setTuneType("Reel")
                .setDefaultKey("D")
                .setDefaultTempo(120);

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(() -> dataSource.findByDomainId(entity.getDomainId()), found -> {
            final var tune = TuneMapper.toDomain(found);

            assertThat(tune.id().value()).isEqualTo(entity.getDomainId());
            assertThat(tune.title().value()).isEqualTo("Arranged Tune");
            assertThat(tune.tuneKind()).isEqualTo(TuneKind.ARRANGEMENT);
            assertThat(tune.defaultComposerCredit().value()).isEqualTo("Original Composer");
            assertThat(tune.defaultArrangerCredit().value()).isEqualTo("Arranger");
            assertThat(tune.originalWorkTitle()).isEqualTo("Original Work");
            assertThat(tune.originalWorkCredit()).isEqualTo("Original Artist");
            assertThat(tune.tuneType()).isEqualTo("Reel");
            assertThat(tune.defaultKey()).isEqualTo("D");
            assertThat(tune.defaultTempo()).isEqualTo(120);
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldMapEntityToDomainWithMinimalFields(UniAsserter asserter) {
        final var entity = new TuneTableRecord()
                .setDomainId(UUID.randomUUID().toString())
                .setTitle("Minimal Tune")
                .setTuneKind("TRAD");

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(() -> dataSource.findByDomainId(entity.getDomainId()), found -> {
            final var tune = TuneMapper.toDomain(found);

            assertThat(tune.tuneKind()).isEqualTo(TuneKind.TRAD);
            assertThat(tune.defaultComposerCredit()).isNull();
            assertThat(tune.defaultArrangerCredit()).isNull();
            assertThat(tune.originalWorkTitle()).isNull();
            assertThat(tune.originalWorkCredit()).isNull();
            assertThat(tune.tuneType()).isNull();
            assertThat(tune.defaultKey()).isNull();
            assertThat(tune.defaultTempo()).isNull();
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldMapDomainToEntityAndPersist(UniAsserter asserter) {
        final var tune = Tune.create(
                TuneTitle.of("Domain Mapped Tune"),
                TuneKind.ORIGINAL,
                Credit.of("Composer"),
                Credit.of("Arranger"),
                null,
                null,
                "Jig",
                "G",
                110);

        final var entity = TuneMapper.toEntity(tune);

        assertThat(entity.getDomainId()).isEqualTo(tune.id().value());
        assertThat(entity.getTitle()).isEqualTo("Domain Mapped Tune");
        assertThat(entity.getTuneKind()).isEqualTo("ORIGINAL");
        assertThat(entity.getDefaultComposerCredit()).isEqualTo("Composer");
        assertThat(entity.getDefaultArrangerCredit()).isEqualTo("Arranger");
        assertThat(entity.getTuneType()).isEqualTo("Jig");
        assertThat(entity.getDefaultKey()).isEqualTo("G");
        assertThat(entity.getDefaultTempo()).isEqualTo(110);

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(() -> dataSource.findByDomainId(tune.id().value()), found -> {
            assertThat(found.getTitle()).isEqualTo("Domain Mapped Tune");
            assertThat(found.getTuneKind()).isEqualTo("ORIGINAL");
        });
    }
}
