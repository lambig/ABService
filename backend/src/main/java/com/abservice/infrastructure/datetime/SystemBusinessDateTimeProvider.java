package com.abservice.infrastructure.datetime;

import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.domain.service.BusinessDateTimeProvider;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;

/**
 * システム時刻ベースのビジネス日時プロバイダー
 *
 * <p>
 * 本番環境で使用するデフォルトの実装。 システムの現在時刻（リアルタイム）を返す。
 * </p>
 */
@ApplicationScoped
public class SystemBusinessDateTimeProvider implements BusinessDateTimeProvider {
    @Override
    public Uni<BusinessDateTime> now() {
        return Uni.createFrom().item(() -> BusinessDateTime.of(Instant.now()));
    }
}
