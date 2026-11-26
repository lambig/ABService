package com.abservice.infrastructure.persistence.repository;

import com.abservice.application.audio.ListeningAuthorization;
import com.abservice.application.port.ListeningAuthorizationStore;
import com.abservice.infrastructure.persistence.entity.ListeningAuthorizationTableRecord;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.hibernate.reactive.mutiny.Mutiny;

/**
 * DB locking makes budgets and one-time issuance work across instances and
 * restarts.
 */
@ApplicationScoped
public class DatabaseListeningAuthorizations implements ListeningAuthorizationStore {
    private static final String DELETE_OLD_REQUESTS = """
            DELETE FROM listening_authorization
            WHERE created_at < clock_timestamp() - interval '24 hours'
            """;
    private static final String LOCK_START_BUDGET = """
            SELECT singleton FROM listening_authorization_budget WHERE singleton = 2 FOR UPDATE
            """;
    private static final String INSERT_WITHIN_BUDGET = """
            INSERT INTO listening_authorization(request_id, label, code_digest, proof_digest)
            SELECT :id, :label, :code, :proof
            WHERE (SELECT count(*) FROM listening_authorization
            WHERE created_at > clock_timestamp() - interval '5 minutes') < 30
            ON CONFLICT DO NOTHING
            """;
    private static final String COUNT_RESOLVE_ATTEMPT = """
            UPDATE listening_authorization_budget SET
            attempts = CASE WHEN window_start <= clock_timestamp() - interval '5 minutes' THEN 1
            ELSE least(attempts + 1, 21) END,
            window_start = CASE WHEN window_start <= clock_timestamp() - interval '5 minutes'
            THEN clock_timestamp()
            ELSE window_start END
            WHERE singleton = 1 RETURNING attempts
            """;
    private static final String FIND_BY_CODE = """
            SELECT request_id, label, state, expires_at, valid_days
            FROM listening_authorization WHERE code_digest = :code
            """;
    private static final String DECIDE_PENDING = """
            UPDATE listening_authorization SET state = :state, valid_days = :days
            WHERE request_id = :id AND state = 'PENDING' AND expires_at > clock_timestamp()
            """;
    private static final String LOCK_BY_PROOF = """
            SELECT request_id, label, state, expires_at, valid_days FROM listening_authorization
            WHERE request_id = :id
            AND proof_digest = :proof FOR UPDATE
            """;
    private static final String RECORD_POLL = """
            UPDATE listening_authorization SET last_poll_at = clock_timestamp()
            WHERE request_id = :id AND (last_poll_at IS NULL
            OR last_poll_at <= clock_timestamp() - interval '5 seconds')
            """;
    private static final String ISSUE_DEVICE = """
            INSERT INTO listening_device(device_id, label, token_digest, expires_at)
            VALUES (:id, :label, :digest, clock_timestamp() + make_interval(days => :days))
            """;
    private final Mutiny.SessionFactory factory;
    public DatabaseListeningAuthorizations(Mutiny.SessionFactory factory) {
        this.factory = factory;
    }
    @Override
    public Uni<ListeningAuthorization> start(UUID id,
            String label,
            String codeDigest,
            String proofDigest) {
        return committed(
                session -> session.createNativeQuery(LOCK_START_BUDGET, Integer.class)
                        .getSingleResult()
                        .chain(
                                () -> session
                                        .createNativeQuery(INSERT_WITHIN_BUDGET)
                                        .setParameter("id", id)
                                        .setParameter("label", label)
                                        .setParameter("code", codeDigest)
                                        .setParameter("proof", proofDigest)
                                        .executeUpdate())
                        .chain(
                                count -> count == 1
                                        ? session.createNativeQuery(DELETE_OLD_REQUESTS).executeUpdate()
                                                .chain(() -> find(session, id))
                                        : Uni.createFrom()
                                                .item(
                                                        ListeningAuthorization.status(
                                                                "RATE_LIMITED",
                                                                300))));
    }
    @Override
    public Uni<ListeningAuthorization> resolve(String codeDigest) {
        return committed(
                session -> session
                        .createNativeQuery(
                                COUNT_RESOLVE_ATTEMPT,
                                Integer.class)
                        .getSingleResult()
                        .chain(
                                attempts -> attempts <= 20
                                        ? session.createNativeQuery(
                                                FIND_BY_CODE,
                                                ListeningAuthorizationTableRecord.class)
                                                .setParameter("code", codeDigest)
                                                .getResultList()
                                                .chain(
                                                        rows -> rows.stream()
                                                                .findFirst()
                                                                .map(
                                                                        row -> view(
                                                                                session,
                                                                                row))
                                                                .orElseGet(
                                                                        () -> Uni.createFrom()
                                                                                .item(
                                                                                        ListeningAuthorization
                                                                                                .status(
                                                                                                        "NOT_FOUND",
                                                                                                        0))))
                                        : Uni.createFrom()
                                                .item(
                                                        ListeningAuthorization.status(
                                                                "RATE_LIMITED",
                                                                300))));
    }
    @Override
    public Uni<ListeningAuthorization> decide(UUID id,
            boolean approve,
            int validDays) {
        return committed(
                session -> session.createNativeQuery(DECIDE_PENDING)
                        .setParameter(
                                "state",
                                approve
                                        ? "APPROVED"
                                        : "DENIED")
                        .setParameter("days", validDays)
                        .setParameter("id", id)
                        .executeUpdate()
                        .chain(
                                () -> find(
                                        session,
                                        id)));
    }
    @Override
    public Uni<ListeningAuthorization> poll(UUID id,
            String proofDigest,
            String tokenDigest) {
        return committed(
                session -> session
                        .createNativeQuery(
                                LOCK_BY_PROOF,
                                ListeningAuthorizationTableRecord.class)
                        .setParameter("id", id)
                        .setParameter("proof", proofDigest)
                        .getResultList()
                        .chain(
                                rows -> rows.stream()
                                        .findFirst()
                                        .map(
                                                row -> pollLocked(
                                                        session,
                                                        row,
                                                        tokenDigest))
                                        .orElseGet(
                                                () -> Uni.createFrom()
                                                        .item(
                                                                ListeningAuthorization.status(
                                                                        "NOT_FOUND",
                                                                        0)))));
    }
    private Uni<ListeningAuthorization> pollLocked(Mutiny.Session session,
            ListeningAuthorizationTableRecord row,
            String tokenDigest) {
        return view(
                session,
                row)
                .chain(
                        current -> "CONSUMED".equals(current.state())
                                ? Uni.createFrom()
                                        .item(current)
                                : session
                                        .createNativeQuery(RECORD_POLL)
                                        .setParameter("id", row.getRequestId())
                                        .executeUpdate()
                                        .chain(
                                                count -> count == 0
                                                        ? Uni.createFrom()
                                                                .item(
                                                                        ListeningAuthorization.status(
                                                                                "SLOW_DOWN",
                                                                                5))
                                                        : "APPROVED".equals(current.state())
                                                                ? issue(
                                                                        session,
                                                                        row,
                                                                        tokenDigest)
                                                                : Uni.createFrom()
                                                                        .item(current)));
    }
    private Uni<ListeningAuthorization> issue(Mutiny.Session session,
            ListeningAuthorizationTableRecord row,
            String tokenDigest) {
        return session.createNativeQuery(ISSUE_DEVICE)
                .setParameter("id", row.getRequestId())
                .setParameter("label", row.getLabel())
                .setParameter("digest", tokenDigest)
                .setParameter("days", row.getValidDays())
                .executeUpdate()
                .chain(
                        () -> session
                                .createNativeQuery(
                                        "UPDATE listening_authorization SET state = 'CONSUMED' WHERE request_id = :id")
                                .setParameter("id", row.getRequestId())
                                .executeUpdate())
                .replaceWith(
                        new ListeningAuthorization("ISSUED",
                                row.getRequestId(),
                                row.getLabel(),
                                row.getExpiresAt(),
                                row.getRequestId(),
                                0));
    }
    private Uni<ListeningAuthorization> find(Mutiny.Session session,
            UUID id) {
        return session.find(
                ListeningAuthorizationTableRecord.class,
                id)
                .map(Optional::ofNullable)
                .chain(
                        row -> row.map(
                                value -> view(
                                        session,
                                        value))
                                .orElseGet(
                                        () -> Uni.createFrom()
                                                .item(
                                                        ListeningAuthorization.status(
                                                                "NOT_FOUND",
                                                                0))));
    }
    private Uni<ListeningAuthorization> view(Mutiny.Session session,
            ListeningAuthorizationTableRecord row) {
        return session
                .createNativeQuery(
                        "SELECT expires_at <= clock_timestamp() FROM listening_authorization WHERE request_id = :id",
                        Boolean.class)
                .setParameter("id", row.getRequestId())
                .getSingleResult()
                .map(
                        expired -> new ListeningAuthorization("CONSUMED".equals(row.getState())
                                ? "CONSUMED"
                                : expired
                                        ? "EXPIRED"
                                        : row.getState(),
                                row.getRequestId(),
                                row.getLabel(),
                                row.getExpiresAt(),
                                "CONSUMED".equals(row.getState())
                                        ? row.getRequestId()
                                        : null,
                                5));
    }
    @SuppressWarnings("PMD.ForbiddenUnusedLambdaParameter") // REACTIVE-API: Hibernate owns the transaction.
    private <T> Uni<T> committed(Function<Mutiny.Session, Uni<T>> work) {
        return factory.openSession()
                .chain(
                        session -> session.withTransaction(_ -> work.apply(session))
                                .eventually(session::close));
    }
}
