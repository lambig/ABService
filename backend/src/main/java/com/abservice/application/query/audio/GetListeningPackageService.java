package com.abservice.application.query.audio;

import com.abservice.application.audio.PrivateAudioAccess;
import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.query.QueryService;
import com.abservice.infrastructure.persistence.datasource.ListeningPackageDataSource;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * 認証した端末へ渡す配布パッケージの照会。1問い合わせの snapshot から Manifest v3 の内容を組む。
 * 機能無効時は未存在として扱う。取得URLはここでは解決しない。
 */
@ApplicationScoped
@AllArgsConstructor
@FailureContract(Failure.NOT_FOUND)
public class GetListeningPackageService
        implements
            QueryService<GetListeningPackageService.Query, GetListeningPackageService.Result> {
    private final PrivateAudioAccess access;
    private final ListeningPackageDataSource snapshots;

    /** 曲名を持たない収録曲の名をチューン名から組むときの区切り。公開サイトの表示と同じ規則にする。 */
    @ConfigProperty(name = "abservice.track.tune-title-separator")
    private final String tuneTitleSeparator;

    @Override
    public Uni<Result> query(Query query) {
        return Uni.createFrom().voidItem()
                .invoke(access::requireEnabled)
                .chain(snapshots::snapshot)
                .map(rows -> new Result(ListeningPackageView.of(rows, tuneTitleSeparator)));
    }

    public record Query() implements QueryService.Query {
    }

    public record Result(ListeningPackageView manifest) implements QueryService.Result {
    }
}
