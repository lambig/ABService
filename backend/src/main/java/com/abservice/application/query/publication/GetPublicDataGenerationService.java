package com.abservice.application.query.publication;

import com.abservice.application.query.QueryService;
import com.abservice.infrastructure.persistence.datasource.PublicDataGenerationDataSource;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;

/** 公開ビルド・配布の前後で保存済み世代を照合するための照会。 */
@ApplicationScoped
@AllArgsConstructor
public class GetPublicDataGenerationService
        implements
            QueryService<GetPublicDataGenerationQuery, GetPublicDataGenerationResult> {

    private final PublicDataGenerationDataSource dataSource;

    @Override
    public Uni<GetPublicDataGenerationResult> query(GetPublicDataGenerationQuery query) {
        return dataSource.readGeneration().map(GetPublicDataGenerationResult::new);
    }
}
