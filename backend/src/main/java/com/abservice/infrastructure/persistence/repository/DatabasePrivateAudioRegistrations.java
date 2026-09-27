package com.abservice.infrastructure.persistence.repository;

import com.abservice.application.port.FlacMetadata;
import com.abservice.application.port.PrivateAudioRegistration;
import com.abservice.application.port.PrivateAudioRegistrations;
import com.abservice.application.port.PrivateAudioMaintenance;
import com.abservice.infrastructure.persistence.entity.PrivateAudioRegistrationTableRecord;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.hibernate.reactive.mutiny.Mutiny;

/** 独立commitと条件付き更新で、保存前の検査記録を確定する。外部I/O中のDBロックは持たない。 */
@ApplicationScoped
public class DatabasePrivateAudioRegistrations implements PrivateAudioRegistrations, PrivateAudioMaintenance {
    private final Mutiny.SessionFactory sessionFactory;

    public DatabasePrivateAudioRegistrations(Mutiny.SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public Uni<Boolean> create(UUID id, Instant expiresAt) {
        return committed(
                session -> session.createNativeQuery("""
                        INSERT INTO private_audio_registration (audio_id, expires_at)
                        VALUES (:id, :expiresAt) ON CONFLICT (audio_id) DO NOTHING
                        """)
                        .setParameter("id", id)
                        .setParameter("expiresAt", expiresAt)
                        .executeUpdate())
                .map(count -> count == 1);
    }

    @Override
    public Uni<Optional<PrivateAudioRegistration>> find(UUID id) {
        return committed(
                session -> session.find(PrivateAudioRegistrationTableRecord.class, id)
                        .map(Optional::ofNullable)
                        .map(row -> row.map(DatabasePrivateAudioRegistrations::registration)));
    }

    @Override
    public Uni<Boolean> recordInspection(UUID id, FlacMetadata metadata) {
        return committed(
                session -> session.createNativeQuery("""
                        UPDATE private_audio_registration SET state = 'INSPECTED',
                            byte_length = :length, sha256 = :hash, sample_rate = :rate,
                            channels = :channels, bits_per_sample = :bits, total_samples = :samples
                        WHERE audio_id = :id AND state = 'PENDING' AND expires_at > clock_timestamp()
                        """)
                        .setParameter("id", id)
                        .setParameter("length", metadata.byteLength())
                        .setParameter("hash", metadata.sha256())
                        .setParameter("rate", metadata.sampleRate())
                        .setParameter("channels", metadata.channels())
                        .setParameter("bits", metadata.bitsPerSample())
                        .setParameter("samples", metadata.totalSamples())
                        .executeUpdate())
                .map(count -> count == 1);
    }

    @Override
    public Uni<Boolean> confirm(UUID id, FlacMetadata storedMetadata) {
        return confirmFrom(
                id,
                storedMetadata,
                false);
    }

    @Override
    public Uni<Boolean> confirmRecovered(UUID id, FlacMetadata metadata) {
        return confirmFrom(
                id,
                metadata,
                true);
    }

    private Uni<Boolean> confirmFrom(
            UUID id,
            FlacMetadata storedMetadata,
            boolean recovery) {
        return committed(
                session -> session.createNativeQuery("""
                        UPDATE private_audio_registration SET state = 'CONFIRMED'
                        WHERE audio_id = :id AND (state = 'INSPECTED' OR (:recovery AND state = 'ABANDONED'))
                            AND byte_length = :length AND sha256 = :hash AND sample_rate = :rate
                            AND channels = :channels AND bits_per_sample = :bits AND total_samples = :samples
                        """)
                        .setParameter("id", id)
                        .setParameter("recovery", recovery)
                        .setParameter("length", storedMetadata.byteLength())
                        .setParameter("hash", storedMetadata.sha256())
                        .setParameter("rate", storedMetadata.sampleRate())
                        .setParameter("channels", storedMetadata.channels())
                        .setParameter("bits", storedMetadata.bitsPerSample())
                        .setParameter("samples", storedMetadata.totalSamples())
                        .executeUpdate())
                .map(count -> count == 1);
    }

    @Override
    public Uni<List<UUID>> staleInspected(Duration retention, int batchSize) {
        final var seconds = retentionSeconds(retention);
        final var bounded = Optional.of(batchSize)
                .filter(size -> size > 0)
                .filter(size -> size <= 1000)
                .orElseThrow(() -> new IllegalArgumentException("Invalid audio maintenance batch"));
        return committed(session -> session.createNativeQuery("""
                SELECT audio_id FROM private_audio_registration
                WHERE state = 'INSPECTED'
                    AND expires_at <= clock_timestamp() - CAST(:seconds AS double precision) * interval '1 second'
                ORDER BY expires_at, audio_id LIMIT :batch
                """, UUID.class).setParameter("seconds", seconds).setParameter("batch", bounded).getResultList())
                .map(List::copyOf);
    }

    @Override
    public Uni<Boolean> abandonInspected(UUID id, Duration retention) {
        final var seconds = retentionSeconds(retention);
        return committed(session -> session.createNativeQuery("""
                UPDATE private_audio_registration SET state = 'ABANDONED'
                WHERE audio_id = :id AND state = 'INSPECTED'
                    AND expires_at <= clock_timestamp() - CAST(:seconds AS double precision) * interval '1 second'
                """).setParameter("id", id).setParameter("seconds", seconds).executeUpdate()).map(count -> count == 1);
    }

    private static long retentionSeconds(Duration retention) {
        return Optional.of(retention.getSeconds())
                .filter(seconds -> seconds >= 3600)
                .filter(seconds -> seconds <= 604800)
                .orElseThrow(() -> new IllegalArgumentException("Audio retention must be 1 hour to 7 days"));
    }

    @Override
    public Uni<List<UUID>> expirePending(int batchSize) {
        final var bounded = Optional.of(batchSize)
                .filter(size -> size > 0)
                .filter(size -> size <= 1000)
                .orElseThrow(() -> new IllegalArgumentException("Expiry batch size must be between 1 and 1000"));
        return committed(session -> session.createNativeQuery("""
                WITH candidates AS (
                    SELECT audio_id FROM private_audio_registration
                    WHERE state = 'PENDING' AND expires_at <= clock_timestamp()
                    ORDER BY expires_at, audio_id LIMIT :batchSize FOR UPDATE SKIP LOCKED
                )
                UPDATE private_audio_registration AS registration SET state = 'EXPIRED'
                FROM candidates WHERE registration.audio_id = candidates.audio_id
                RETURNING registration.audio_id
                """, UUID.class).setParameter("batchSize", bounded).getResultList()).map(List::copyOf);
    }

    /** 呼出元の未commitトランザクションへ合流せず、応答前に独立commitとsession解放を完了する。 */
    @SuppressWarnings("PMD.ForbiddenUnusedLambdaParameter") // REACTIVE-API: 必須のTransaction引数の制御はAPIへ任せる。
    private <T> Uni<T> committed(Function<Mutiny.Session, Uni<T>> work) {
        return sessionFactory.openSession().chain(
                session -> session.withTransaction(_ -> work.apply(session))
                        .eventually(session::close));
    }

    private static PrivateAudioRegistration registration(PrivateAudioRegistrationTableRecord row) {
        return new PrivateAudioRegistration(row.getAudioId(), row.getCreatedAt(), row.getExpiresAt(),
                switch (row.getState()) {
                    case "PENDING" -> new PrivateAudioRegistration.Pending();
                    case "INSPECTED" -> new PrivateAudioRegistration.Inspected(metadata(row));
                    case "CONFIRMED" -> new PrivateAudioRegistration.Confirmed(metadata(row));
                    case "EXPIRED" -> new PrivateAudioRegistration.Expired();
                    case "ABANDONED" -> new PrivateAudioRegistration.Abandoned(metadata(row));
                    default -> throw new IllegalStateException("Unknown private audio registration state");
                });
    }

    private static FlacMetadata metadata(PrivateAudioRegistrationTableRecord row) {
        return new FlacMetadata(row.getByteLength(), row.getSha256(), row.getSampleRate(), row.getChannels(),
                row.getBitsPerSample(), row.getTotalSamples());
    }
}
