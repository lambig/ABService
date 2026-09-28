package com.abservice.infrastructure.persistence.repository;

import com.abservice.application.port.PublishedAsset;
import com.abservice.application.port.PublishedAssets;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Function;
import org.hibernate.reactive.mutiny.Mutiny;

/** 独立commitで確定アセットの実測値を記録する。確定フローは要求のsessionを持たないため、ここで開いて閉じる。 */
@ApplicationScoped
public class DatabasePublishedAssets implements PublishedAssets {
    private final Mutiny.SessionFactory sessionFactory;

    public DatabasePublishedAssets(Mutiny.SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public Uni<Void> record(PublishedAsset asset) {
        return committed(
                session -> session.createNativeQuery("""
                        INSERT INTO published_asset (asset_key, content_type, byte_length, sha256)
                        VALUES (:key, :contentType, :byteLength, :sha256)
                        ON CONFLICT (asset_key) DO UPDATE SET content_type = EXCLUDED.content_type,
                            byte_length = EXCLUDED.byte_length, sha256 = EXCLUDED.sha256,
                            confirmed_at = clock_timestamp()
                        """)
                        .setParameter("key", asset.assetKey())
                        .setParameter("contentType", asset.contentType())
                        .setParameter("byteLength", asset.byteLength())
                        .setParameter("sha256", asset.sha256())
                        .executeUpdate())
                .replaceWithVoid();
    }

    /** 呼出元の未commitトランザクションへ合流せず、応答前に独立commitとsession解放を完了する。 */
    @SuppressWarnings("PMD.ForbiddenUnusedLambdaParameter") // REACTIVE-API: 必須のTransaction引数の制御はAPIへ任せる。
    private <T> Uni<T> committed(Function<Mutiny.Session, Uni<T>> work) {
        return sessionFactory.openSession().chain(
                session -> session.withTransaction(_ -> work.apply(session))
                        .eventually(session::close));
    }
}
