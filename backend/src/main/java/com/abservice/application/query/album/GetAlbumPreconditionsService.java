package com.abservice.application.query.album;

import com.abservice.application.query.Audience;
import com.abservice.application.query.AudienceVisibility;
import com.abservice.application.query.QueryService;
import com.abservice.application.query.album.model.DeletionEffectView;
import com.abservice.application.query.album.model.UnpublicationEffectView;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.service.AlbumDeletionService;
import com.abservice.domain.service.AlbumDeletionService.AlbumDeletion;
import com.abservice.domain.service.AlbumDeletionService.ArticleEffect;
import com.abservice.domain.service.AlbumUnpublicationService;
import com.abservice.domain.service.AlbumUnpublicationService.AlbumUnpublication;
import com.abservice.infrastructure.persistence.datasource.AlbumDataSource;
import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * アルバムに対する操作の前提を問う照会サービス
 *
 * <p>
 * 管理画面が破壊的な操作の前に投げる「この操作をして問題ないか」に答えます（#274）。
 * </p>
 *
 * <p>
 * READ-THROUGH-DOMAIN: 他の照会（{@link GetAlbumService} 等）と違い、Read Model
 * だけでは答えられません。 答えは業務上の判定——どの記事が参照を失い、どれが非公開へ戻るか——であり、これを Read Model の上で組み立て直すと
 * 実行時の判定と乖離します。判定は実行するコマンドと同じドメインサービス（{@link AlbumDeletionService} /
 * {@link AlbumUnpublicationService}）の操作オブジェクトから受け取ります。{@code QueryService}
 * の原則のうち 「Read Model から取得」だけがこの理由で外れます（副作用が無いこと・照会結果 DTO を返すことは満たします）。
 * </p>
 *
 * <p>
 * 存在確認だけは Read Model で行います。ここで要るのは「あるか」だけで、アルバム集約そのものは判定に使わないためです
 * （集約の取得は主張を伴う入口に限られる——{@code docs/DECISIONS.md} 15）。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class GetAlbumPreconditionsService
        implements
            QueryService<GetAlbumPreconditionsQuery, GetAlbumPreconditionsResult> {

    private final AlbumDataSource dataSource;
    private final AlbumDeletionService albumDeletionService;
    private final AlbumUnpublicationService albumUnpublicationService;

    @WithSession
    @Override
    public Uni<GetAlbumPreconditionsResult> query(GetAlbumPreconditionsQuery query) {
        return dataSource.findByDomainId(query.albumId(), AudienceVisibility.of(Audience.ADMIN))
                .flatMap(found -> toResult(found, query));
    }

    private Uni<GetAlbumPreconditionsResult> toResult(
            @Nullable AlbumTableRecord found,
            GetAlbumPreconditionsQuery query) {
        return Optional.ofNullable(found)
                .map(existing -> Album.Id.of(existing.getDomainId()))
                .map(albumId -> preconditionsOf(albumId, query.operation()))
                .orElseGet(() -> Uni.createFrom().item(new GetAlbumPreconditionsResult.NotFound()));
    }

    private Uni<GetAlbumPreconditionsResult> preconditionsOf(Album.Id albumId, AlbumOperation operation) {
        return switch (operation) {
            case DELETE -> deletionPreconditions(albumId);
            case UNPUBLISH -> unpublicationPreconditions(albumId);
        };
    }

    private Uni<GetAlbumPreconditionsResult> deletionPreconditions(Album.Id albumId) {
        return albumDeletionService.attempt(albumId)
                .map(GetAlbumPreconditionsService::toDeletion);
    }

    private Uni<GetAlbumPreconditionsResult> unpublicationPreconditions(Album.Id albumId) {
        return albumUnpublicationService.attempt(albumId)
                .map(GetAlbumPreconditionsService::toUnpublication);
    }

    private static GetAlbumPreconditionsResult toDeletion(AlbumDeletion deletion) {
        return new GetAlbumPreconditionsResult.Deletion(
                deletion.effects().stream()
                        .map(GetAlbumPreconditionsService::toDeletionEffectView)
                        .toList());
    }

    private static DeletionEffectView toDeletionEffectView(ArticleEffect effect) {
        return new DeletionEffectView(
                effect.article().id().value(),
                effect.article().title().value(),
                effect.losesAlbumReference(),
                effect.becomesUnpublished());
    }

    private static GetAlbumPreconditionsResult toUnpublication(AlbumUnpublication unpublication) {
        return new GetAlbumPreconditionsResult.Unpublication(
                unpublication.articlesBecomingUnpublished().stream()
                        .map(GetAlbumPreconditionsService::toUnpublicationEffectView)
                        .toList());
    }

    private static UnpublicationEffectView toUnpublicationEffectView(Article article) {
        return new UnpublicationEffectView(
                article.id().value(),
                article.title().value());
    }
}
