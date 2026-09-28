package com.abservice.infrastructure.persistence.repository;

import static com.abservice.lib.Iterables.toList;

import com.abservice.application.port.ListeningDevice;
import com.abservice.application.port.ListeningDevices;
import com.abservice.infrastructure.persistence.entity.ListeningDeviceTableRecord;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.hibernate.reactive.mutiny.Mutiny;

/** 独立commitと条件付き更新で端末資格情報を扱う。認証時の照合も要求のsessionを持たずに実行できる。 */
@ApplicationScoped
public class DatabaseListeningDevices implements ListeningDevices {
    private final Mutiny.SessionFactory sessionFactory;

    public DatabaseListeningDevices(Mutiny.SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public Uni<Boolean> create(
            UUID id,
            String label,
            String tokenDigest,
            Instant expiresAt) {
        return committed(
                session -> session.createNativeQuery("""
                        INSERT INTO listening_device (device_id, label, token_digest, expires_at)
                        VALUES (:id, :label, :digest, :expiresAt) ON CONFLICT (device_id) DO NOTHING
                        """)
                        .setParameter("id", id)
                        .setParameter("label", label)
                        .setParameter("digest", tokenDigest)
                        .setParameter("expiresAt", expiresAt)
                        .executeUpdate())
                .map(count -> count == 1);
    }

    @Override
    public Uni<Optional<ListeningDevice>> find(UUID id) {
        return committed(
                session -> session.find(ListeningDeviceTableRecord.class, id)
                        .map(Optional::ofNullable)
                        .map(row -> row.map(DatabaseListeningDevices::device)));
    }

    @Override
    public Uni<List<ListeningDevice>> list() {
        return committed(
                session -> session.createQuery("""
                        SELECT d FROM ListeningDeviceTableRecord d ORDER BY d.createdAt DESC, d.deviceId DESC
                        """, ListeningDeviceTableRecord.class).getResultList())
                .map(toList(DatabaseListeningDevices::device));
    }

    @Override
    public Uni<Boolean> revoke(UUID id) {
        return committed(
                session -> session.createNativeQuery("""
                        UPDATE listening_device SET revoked_at = clock_timestamp()
                        WHERE device_id = :id AND revoked_at IS NULL
                        """)
                        .setParameter("id", id)
                        .executeUpdate())
                .map(count -> count == 1);
    }

    @Override
    public Uni<Optional<ListeningDevice>> findActiveByDigest(String tokenDigest) {
        return committed(
                session -> session.createNativeQuery("""
                        SELECT * FROM listening_device
                        WHERE token_digest = :digest AND revoked_at IS NULL AND expires_at > clock_timestamp()
                        """, ListeningDeviceTableRecord.class)
                        .setParameter("digest", tokenDigest)
                        .getResultList())
                .map(rows -> rows.stream().findFirst().map(DatabaseListeningDevices::device));
    }

    /** 呼出元の未commitトランザクションへ合流せず、応答前に独立commitとsession解放を完了する。 */
    @SuppressWarnings("PMD.ForbiddenUnusedLambdaParameter") // REACTIVE-API: 必須のTransaction引数の制御はAPIへ任せる。
    private <T> Uni<T> committed(Function<Mutiny.Session, Uni<T>> work) {
        return sessionFactory.openSession().chain(
                session -> session.withTransaction(_ -> work.apply(session))
                        .eventually(session::close));
    }

    private static ListeningDevice device(ListeningDeviceTableRecord row) {
        return new ListeningDevice(row.getDeviceId(), row.getLabel(), row.getCreatedAt(), row.getExpiresAt(),
                row.getRevokedAt());
    }
}
