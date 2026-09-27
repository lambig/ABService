package com.abservice.infrastructure.audio;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.application.port.FlacInspectionLimits;
import com.abservice.application.port.FlacMetadata;
import com.abservice.application.port.InspectedAudio;
import com.abservice.application.port.PrivateAudioRegistration;
import com.abservice.application.port.PrivateAudioStorage;
import com.abservice.infrastructure.persistence.repository.DatabasePrivateAudioRegistrations;
import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.vertx.core.Context;
import jakarta.inject.Inject;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** 実DBの独立sessionとworker往復を検証する。音源・保存先は合成の境界fixture。 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
class PrivateAudioIngestionIntegrationTest {
    private static final FlacMetadata METADATA = new FlacMetadata(
            3,
            "a".repeat(64),
            44100,
            2,
            16,
            44100);
    @Inject
    private DatabasePrivateAudioRegistrations registrations;

    @Test
    @DisplayName("実DBのpendingからworkerで検査・保存を経由し独立sessionで確定状態を読める")
    @RunOnVertxContext
    @SuppressWarnings("PMD.ForbiddenUnusedLambdaParameter") // TEST-DOUBLE: 実デコードは専用統合テストへ委ね、ここではDB接続を検査する。
    void completesThroughWorkerAndDatabase(UniAsserter asserter) {
        final var id = UUID.randomUUID();
        final var storage = new Storage();
        final var closed = new AtomicInteger();
        final var service = new PrivateAudioIngestion(registrations, (input, limits) -> {
            assertThat(Context.isOnEventLoopThread()).isFalse();
            return new Snapshot(closed);
        }, storage, 1, 2);
        asserter.execute(() -> registrations.create(id, Instant.now().plusSeconds(600)));
        asserter.assertThat(
                () -> service.ingest(
                        id,
                        InputStream::nullInputStream,
                        FlacInspectionLimits.defaults()),
                metadata -> assertThat(metadata).isEqualTo(METADATA));
        asserter.assertThat(() -> registrations.find(id), row -> {
            assertThat(row.orElseThrow().state()).isEqualTo(new PrivateAudioRegistration.Confirmed(METADATA));
            assertThat(closed.get()).isEqualTo(1);
            assertThat(storage.writes.get()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("先行commitだけ残した登録を別coordinatorから復旧し再確定を拒否する")
    @RunOnVertxContext
    void recoversPersistedInspection(UniAsserter asserter) {
        final var id = UUID.randomUUID();
        final var storage = new Storage();
        storage.stored.set(METADATA);
        final var service = new PrivateAudioIngestion(registrations, (input, limits) -> {
            throw new AssertionError("Recovery must not inspect input");
        }, storage, 1, 2);
        asserter.execute(() -> registrations.create(id, Instant.now().plusSeconds(600)));
        asserter.execute(() -> registrations.recordInspection(id, METADATA));
        asserter.assertThat(() -> service.recover(id), metadata -> assertThat(metadata).isEqualTo(METADATA));
        asserter.assertThat(() -> registrations.find(id), row -> {
            assertThat(row.orElseThrow().state()).isEqualTo(new PrivateAudioRegistration.Confirmed(METADATA));
            assertThat(storage.writes.get()).isZero();
        });
        asserter.assertFailedWith(() -> service.recover(id), IllegalStateException.class);
    }

    private record Snapshot(AtomicInteger closed) implements InspectedAudio {
        @Override
        public FlacMetadata metadata() {
            return METADATA;
        }

        @Override
        public InputStream openStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public void close() {
            assertThat(Context.isOnEventLoopThread()).isFalse();
            closed.incrementAndGet();
        }
    }

    private static final class Storage implements PrivateAudioStorage {
        private final AtomicReference<FlacMetadata> stored = new AtomicReference<>();
        private final AtomicInteger writes = new AtomicInteger();

        @Override
        public void write(UUID id, InspectedAudio snapshot) {
            assertThat(Context.isOnEventLoopThread()).isFalse();
            stored.set(snapshot.metadata());
            writes.incrementAndGet();
        }

        @Override
        public Optional<FlacMetadata> find(UUID id) {
            assertThat(Context.isOnEventLoopThread()).isFalse();
            return Optional.ofNullable(stored.get());
        }
    }
}
