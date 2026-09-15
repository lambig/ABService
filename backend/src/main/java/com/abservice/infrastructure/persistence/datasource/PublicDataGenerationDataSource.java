package com.abservice.infrastructure.persistence.datasource;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;
import org.hibernate.reactive.mutiny.Mutiny;

/** 公開Queryが読むデータの保存済み世代をprimary DBから取得する。 */
@ApplicationScoped
@AllArgsConstructor
public class PublicDataGenerationDataSource {

    private final Mutiny.SessionFactory sessionFactory;

    /**
     * @return 保存済み世代。単一行が欠けた場合は正常値を作らず失敗する
     */
    public Uni<String> readGeneration() {
        return sessionFactory.withSession(
                session -> session.createNativeQuery(
                        "SELECT cast(generation AS text) FROM public_data_generation WHERE singleton",
                        String.class)
                        .getSingleResult());
    }
}
