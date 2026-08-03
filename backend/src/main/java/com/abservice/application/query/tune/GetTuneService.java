package com.abservice.application.query.tune;

import com.abservice.application.query.QueryService;
import com.abservice.infrastructure.persistence.datasource.TuneDataSource;
import com.abservice.infrastructure.persistence.entity.TuneTableRecord;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * チューン詳細照会サービス
 *
 * <p>
 * CQRS の Read 側ユースケース。ドメイン・Repository を経由せず、{@link TuneDataSource} で直接読み取り、
 * {@link GetTuneResult} を返します。未存在は例外ではなく {@link GetTuneResult.NotFound}
 * として返します。
 * </p>
 */
@ApplicationScoped
public class GetTuneService implements QueryService<GetTuneQuery, GetTuneResult> {

    private final TuneDataSource dataSource;

    /**
     * @param dataSource
     *            チューンデータソース（Read）
     */
    public GetTuneService(TuneDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @WithSession
    @Override
    public Uni<GetTuneResult> query(GetTuneQuery query) {
        return dataSource.findByDomainId(query.tuneId())
                .map(GetTuneService::toResult);
    }

    static GetTuneResult toResult(@Nullable TuneTableRecord entity) {
        return Optional.ofNullable(entity)
                .map(TuneViewMapper::toView)
                .<GetTuneResult>map(GetTuneResult.Found::new)
                .orElseGet(GetTuneResult.NotFound::new);
    }
}
