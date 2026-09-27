package com.abservice.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.application.port.FlacMetadata;
import com.abservice.application.port.PrivateAudioRegistration;
import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** 実PostgreSQL上でcommit境界・競合・検査前の期限切れを検査する。保存先への書込はB2bで接続する。 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
class DatabasePrivateAudioRegistrationsIntegrationTest {
    private static final FlacMetadata METADATA = new FlacMetadata(
            100,
            "a".repeat(64),
            48000,
            2,
            24,
            48000);

    @Inject
    private DatabasePrivateAudioRegistrations registrations;
    @Inject
    private Mutiny.SessionFactory sessionFactory;

    @Test
    @DisplayName("予約・検査結果・一致した実体の確定を独立sessionから復元できる")
    @RunOnVertxContext
    void shouldPersistInspectionBeforeConfirmation(UniAsserter asserter) {
        final var id = UUID.randomUUID();
        asserter.assertThat(() -> registrations.find(id), found -> assertThat(found).isEmpty());
        asserter.assertThat(() -> registrations.create(id, future()), created -> assertThat(created).isTrue());
        asserter.assertThat(() -> registrations.find(id), found -> {
            assertThat(found).isPresent();
            assertThat(found.orElseThrow().state()).isEqualTo(new PrivateAudioRegistration.Pending());
            assertThat(found.orElseThrow().createdAt()).isBefore(found.orElseThrow().expiresAt());
        });
        asserter.assertThat(() -> registrations.confirm(id, METADATA), confirmed -> assertThat(confirmed).isFalse());
        asserter.assertThat(
                () -> registrations.recordInspection(id, METADATA),
                recorded -> assertThat(recorded).isTrue());
        asserter.assertThat(
                () -> registrations.find(id),
                found -> assertThat(found.orElseThrow().state())
                        .isEqualTo(new PrivateAudioRegistration.Inspected(METADATA)));
        asserter.assertThat(() -> registrations.confirm(id, METADATA), confirmed -> assertThat(confirmed).isTrue());
        asserter.assertThat(
                () -> registrations.find(id),
                found -> assertThat(found.orElseThrow().state())
                        .isEqualTo(new PrivateAudioRegistration.Confirmed(METADATA)));
        asserter.assertThat(() -> registrations.confirm(id, METADATA), confirmed -> assertThat(confirmed).isFalse());
        asserter.assertThat(
                () -> registrations.recordInspection(id, METADATA),
                recorded -> assertThat(recorded).isFalse());
    }

    @Test
    @DisplayName("予約の再送は状態と期限をリセットしない")
    @RunOnVertxContext
    void shouldRejectDuplicateReservation(UniAsserter asserter) {
        final var id = UUID.randomUUID();
        asserter.execute(() -> registrations.create(id, future()));
        asserter.execute(() -> registrations.recordInspection(id, METADATA));
        asserter.assertThat(
                () -> registrations.find(id).chain(
                        before -> registrations.create(id, future().plusSeconds(3600))
                                .chain(
                                        created -> registrations.find(id)
                                                .map(after -> List.of(created, before.equals(after))))),
                result -> assertThat(result).containsExactly(false, true));
    }

    @Test
    @DisplayName("照合ではhashだけでなく全実測値の不一致を拒否し、検査結果を維持する")
    @RunOnVertxContext
    void shouldRejectEveryMetadataMismatch(UniAsserter asserter) {
        final var id = UUID.randomUUID();
        final var mismatches = List.of(
                new FlacMetadata(
                        101,
                        METADATA.sha256(),
                        48000,
                        2,
                        24,
                        48000),
                new FlacMetadata(
                        100,
                        "b".repeat(64),
                        48000,
                        2,
                        24,
                        48000),
                new FlacMetadata(
                        100,
                        METADATA.sha256(),
                        44100,
                        2,
                        24,
                        48000),
                new FlacMetadata(
                        100,
                        METADATA.sha256(),
                        48000,
                        1,
                        24,
                        48000),
                new FlacMetadata(
                        100,
                        METADATA.sha256(),
                        48000,
                        2,
                        16,
                        48000),
                new FlacMetadata(
                        100,
                        METADATA.sha256(),
                        48000,
                        2,
                        24,
                        48001));
        asserter.execute(() -> registrations.create(id, future()));
        asserter.execute(() -> registrations.recordInspection(id, METADATA));
        mismatches.forEach(
                value -> asserter.assertThat(
                        () -> registrations.confirm(id, value),
                        confirmed -> assertThat(confirmed).isFalse()));
        asserter.assertThat(
                () -> registrations.find(id),
                found -> assertThat(found.orElseThrow().state())
                        .isEqualTo(new PrivateAudioRegistration.Inspected(METADATA)));
    }

    @Test
    @DisplayName("別々の接続で競合する検査結果は一方だけcommitし、その結果だけを確定できる")
    @RunOnVertxContext
    void shouldCommitOnlyOneConcurrentInspection(UniAsserter asserter) {
        final var id = UUID.randomUUID();
        final var other = new FlacMetadata(
                200,
                "b".repeat(64),
                44100,
                1,
                16,
                44100);
        asserter.execute(() -> registrations.create(id, future()));
        asserter.assertThat(
                () -> Uni.combine().all().unis(
                        registrations.recordInspection(id, METADATA),
                        registrations.recordInspection(id, other)).asTuple(),
                results -> assertThat(List.of(results.getItem1(), results.getItem2()))
                        .containsExactlyInAnyOrder(true, false));
        asserter.assertThat(
                () -> Uni.combine().all().unis(
                        registrations.confirm(id, METADATA),
                        registrations.confirm(id, other)).asTuple(),
                results -> assertThat(List.of(results.getItem1(), results.getItem2()))
                        .containsExactlyInAnyOrder(true, false));
        asserter.assertThat(
                () -> registrations.find(id),
                found -> assertThat(found.orElseThrow().state())
                        .isIn(
                                new PrivateAudioRegistration.Confirmed(METADATA),
                                new PrivateAudioRegistration.Confirmed(other)));
    }

    @Test
    @DisplayName("同じ内容の並行確定でも一方だけ成功する")
    @RunOnVertxContext
    void shouldRejectConcurrentReconfirmation(UniAsserter asserter) {
        final var id = UUID.randomUUID();
        asserter.execute(() -> registrations.create(id, future()));
        asserter.execute(() -> registrations.recordInspection(id, METADATA));
        asserter.assertThat(
                () -> Uni.combine().all().unis(
                        registrations.confirm(id, METADATA),
                        registrations.confirm(id, METADATA)).asTuple(),
                results -> assertThat(List.of(results.getItem1(), results.getItem2()))
                        .containsExactlyInAnyOrder(true, false));
    }

    @Test
    @DisplayName("期限切れpendingだけを上限付きで失効し、検査済み実体の復旧は妨げない")
    @RunOnVertxContext
    void shouldExpireOnlyUninspectedReservations(UniAsserter asserter) {
        final var first = UUID.randomUUID();
        final var second = UUID.randomUUID();
        final var inspected = UUID.randomUUID();
        final var confirmed = UUID.randomUUID();
        final var fresh = UUID.randomUUID();
        List.of(
                first,
                second,
                inspected,
                confirmed)
                .forEach(id -> asserter.execute(() -> registrations.create(id, future())));
        asserter.execute(() -> registrations.recordInspection(inspected, METADATA));
        asserter.execute(() -> registrations.recordInspection(confirmed, METADATA));
        asserter.execute(() -> registrations.confirm(confirmed, METADATA));
        asserter.execute(this::ageReservations);
        asserter.execute(() -> registrations.create(fresh, future()));
        asserter.assertThat(
                () -> registrations.recordInspection(first, METADATA),
                recorded -> assertThat(recorded).isFalse());
        asserter.assertThat(
                () -> Uni.combine().all().unis(registrations.expirePending(1), registrations.expirePending(1))
                        .asTuple(),
                expired -> {
                    assertThat(expired.getItem1()).hasSize(1);
                    assertThat(expired.getItem2()).hasSize(1);
                    assertThat(List.of(expired.getItem1().getFirst(), expired.getItem2().getFirst()))
                            .containsExactlyInAnyOrder(first, second);
                });
        asserter.assertThat(() -> registrations.expirePending(1000), expired -> assertThat(expired).isEmpty());
        asserter.assertThat(
                () -> registrations.find(first),
                found -> assertThat(found.orElseThrow().state())
                        .isEqualTo(new PrivateAudioRegistration.Expired()));
        asserter.assertThat(
                () -> registrations.find(fresh),
                found -> assertThat(found.orElseThrow().state())
                        .isEqualTo(new PrivateAudioRegistration.Pending()));
        asserter.assertThat(
                () -> registrations.confirm(inspected, METADATA),
                recovered -> assertThat(recovered).isTrue());
        asserter.assertThat(
                () -> registrations.find(confirmed),
                found -> assertThat(found.orElseThrow().state())
                        .isEqualTo(new PrivateAudioRegistration.Confirmed(METADATA)));
        asserter.assertThat(
                () -> registrations.recordInspection(first, METADATA),
                recorded -> assertThat(recorded).isFalse());
    }

    @Test
    @DisplayName("不正な検査値はDB制約で拒否し、トランザクションをrollbackする")
    @RunOnVertxContext
    void shouldRollbackInvalidMetadata(UniAsserter asserter) {
        final var id = UUID.randomUUID();
        final var invalid = List.of(
                new FlacMetadata(
                        0,
                        METADATA.sha256(),
                        48000,
                        2,
                        24,
                        48000),
                new FlacMetadata(
                        268435457,
                        METADATA.sha256(),
                        48000,
                        2,
                        24,
                        48000),
                new FlacMetadata(
                        100,
                        "INVALID",
                        48000,
                        2,
                        24,
                        48000),
                new FlacMetadata(
                        100,
                        METADATA.sha256(),
                        7999,
                        2,
                        24,
                        48000),
                new FlacMetadata(
                        100,
                        METADATA.sha256(),
                        96001,
                        2,
                        24,
                        48000),
                new FlacMetadata(
                        100,
                        METADATA.sha256(),
                        48000,
                        3,
                        24,
                        48000),
                new FlacMetadata(
                        100,
                        METADATA.sha256(),
                        48000,
                        2,
                        32,
                        48000),
                new FlacMetadata(
                        100,
                        METADATA.sha256(),
                        48000,
                        2,
                        24,
                        0),
                new FlacMetadata(
                        100,
                        METADATA.sha256(),
                        48000,
                        2,
                        24,
                        48000L * 7200 + 1));
        asserter.execute(() -> registrations.create(id, future()));
        invalid.forEach(
                metadata -> asserter.assertFailedWith(
                        () -> registrations.recordInspection(id, metadata),
                        failure -> assertThat(failure).hasMessageContaining("private_audio_metadata")));
        asserter.assertThat(
                () -> registrations.find(id),
                found -> assertThat(found.orElseThrow().state())
                        .isEqualTo(new PrivateAudioRegistration.Pending()));
        asserter.assertThat(
                () -> registrations.recordInspection(id, METADATA),
                recorded -> assertThat(recorded).isTrue());
    }

    @Test
    @DisplayName("過去の予約期限を拒否し、未知IDを確定・検査済みにできない")
    @RunOnVertxContext
    void shouldRejectPastExpiryAndUnknownIds(UniAsserter asserter) {
        final var id = UUID.randomUUID();
        asserter.assertFailedWith(
                () -> registrations.create(id, Instant.EPOCH),
                failure -> assertThat(failure).hasMessageContaining("private_audio_expiry"));
        asserter.assertThat(() -> registrations.find(id), found -> assertThat(found).isEmpty());
        asserter.assertThat(
                () -> registrations.recordInspection(id, METADATA),
                recorded -> assertThat(recorded).isFalse());
        asserter.assertThat(() -> registrations.confirm(id, METADATA), confirmed -> assertThat(confirmed).isFalse());
    }

    @Test
    @DisplayName("呼出元のrollbackに巻き込まれず検査記録がcommit済みで応答する")
    @RunOnVertxContext
    void shouldCommitIndependentlyOfCallerTransaction(UniAsserter asserter) {
        final var id = UUID.randomUUID();
        asserter.execute(() -> registrations.create(id, future()));
        asserter.assertThat(
                () -> sessionFactory.withSession(
                        session -> session.withTransaction(
                                transaction -> registrations
                                        .recordInspection(id, METADATA).invoke(transaction::markForRollback))),
                recorded -> assertThat(recorded).isTrue());
        asserter.assertThat(
                () -> registrations.find(id),
                found -> assertThat(found.orElseThrow().state())
                        .isEqualTo(new PrivateAudioRegistration.Inspected(METADATA)));
    }

    @Test
    @DisplayName("清掃batchの無制限化や空実行を拒否する")
    void shouldBoundExpiryBatch() {
        List.of(
                -1,
                0,
                1001,
                Integer.MAX_VALUE).forEach(
                        size -> assertThatThrownBy(() -> registrations.expirePending(size))
                                .isInstanceOf(IllegalArgumentException.class));
    }

    @SuppressWarnings("PMD.ForbiddenUnusedLambdaParameter") // REACTIVE-API: 固定fixture更新のcommitはAPIへ任せる。
    private Uni<Integer> ageReservations() {
        return sessionFactory.withTransaction((session, _) -> session.createNativeQuery("""
                UPDATE private_audio_registration
                SET created_at = clock_timestamp() - interval '2 hours',
                    expires_at = clock_timestamp() - interval '1 hour'
                """).executeUpdate());
    }

    private static Instant future() {
        return Instant.now().plusSeconds(3600);
    }

    @Test
    @DisplayName("古い検査記録だけを有界抽出し、受付終了後も一致した実体を明示復旧できる")
    @RunOnVertxContext
    void abandonsWithoutLosingRecoveryEvidence(UniAsserter asserter) {
        final var id = UUID.randomUUID();
        final var second = UUID.randomUUID();
        final var fresh = UUID.randomUUID();
        final var retention = Duration.ofHours(24);
        asserter.execute(() -> registrations.create(id, future()));
        asserter.execute(() -> registrations.create(second, future()));
        asserter.execute(() -> registrations.recordInspection(id, METADATA));
        asserter.execute(() -> registrations.recordInspection(second, METADATA));
        asserter.execute(this::ageBeyondRetention);
        asserter.execute(() -> registrations.create(fresh, future()));
        asserter.execute(() -> registrations.recordInspection(fresh, METADATA));
        asserter.assertThat(() -> registrations.staleInspected(retention, 1), ids -> assertThat(ids).hasSize(1));
        asserter.assertThat(
                () -> registrations.abandonInspected(fresh, retention),
                changed -> assertThat(changed).isFalse());
        asserter.assertThat(
                () -> registrations.abandonInspected(id, retention),
                changed -> assertThat(changed).isTrue());
        asserter.assertThat(
                () -> registrations.find(id),
                row -> assertThat(row.orElseThrow().state())
                        .isEqualTo(new PrivateAudioRegistration.Abandoned(METADATA)));
        asserter.assertThat(
                () -> registrations.staleInspected(retention, 10),
                ids -> assertThat(ids).containsExactly(second));
        asserter.assertThat(() -> registrations.confirm(id, METADATA), changed -> assertThat(changed).isFalse());
        asserter.assertThat(
                () -> registrations.recordInspection(id, METADATA),
                changed -> assertThat(changed).isFalse());
        asserter.assertThat(
                () -> registrations.confirmRecovered(
                        id,
                        new FlacMetadata(
                                101,
                                METADATA.sha256(),
                                48000,
                                2,
                                24,
                                48000)),
                changed -> assertThat(changed).isFalse());
        asserter.assertThat(
                () -> registrations.confirmRecovered(id, METADATA),
                changed -> assertThat(changed).isTrue());
        asserter.assertThat(
                () -> registrations.confirmRecovered(id, METADATA),
                changed -> assertThat(changed).isFalse());
    }

    @Test
    @DisplayName("通常確定と受付終了が競合しても一方だけ遷移し検査結果を失わない")
    @RunOnVertxContext
    void arbitratesConfirmationAndAbandonment(UniAsserter asserter) {
        final var id = UUID.randomUUID();
        asserter.execute(() -> registrations.create(id, future()));
        asserter.execute(() -> registrations.recordInspection(id, METADATA));
        asserter.execute(this::ageBeyondRetention);
        asserter.assertThat(
                () -> Uni.combine().all().unis(
                        registrations.confirm(id, METADATA),
                        registrations.abandonInspected(id, Duration.ofHours(24))).asTuple(),
                result -> assertThat(List.of(result.getItem1(), result.getItem2()))
                        .containsExactlyInAnyOrder(true, false));
        asserter.execute(() -> registrations.confirmRecovered(id, METADATA));
        asserter.assertThat(
                () -> registrations.find(id),
                row -> assertThat(row.orElseThrow().state())
                        .isEqualTo(new PrivateAudioRegistration.Confirmed(METADATA)));
    }

    @Test
    @DisplayName("保持期間と照合batchの範囲外設定を拒否する")
    void boundsRetention() {
        List.of(
                Duration.ZERO,
                Duration.ofMinutes(59),
                Duration.ofDays(8)).forEach(retention -> {
                    assertThatThrownBy(() -> registrations.staleInspected(retention, 1))
                            .isInstanceOf(IllegalArgumentException.class);
                    assertThatThrownBy(() -> registrations.abandonInspected(UUID.randomUUID(), retention))
                            .isInstanceOf(IllegalArgumentException.class);
                });
        List.of(0, 1001).forEach(
                size -> assertThatThrownBy(() -> registrations.staleInspected(Duration.ofHours(24), size))
                        .isInstanceOf(IllegalArgumentException.class));
    }

    @SuppressWarnings("PMD.ForbiddenUnusedLambdaParameter") // REACTIVE-API: 固定fixture更新のcommitはAPIへ任せる。
    private Uni<Integer> ageBeyondRetention() {
        return sessionFactory.withTransaction((session, _) -> session.createNativeQuery("""
                UPDATE private_audio_registration
                SET created_at = clock_timestamp() - interval '3 days',
                    expires_at = clock_timestamp() - interval '2 days'
                """).executeUpdate());
    }
}
