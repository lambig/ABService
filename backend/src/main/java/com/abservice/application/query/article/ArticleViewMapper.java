package com.abservice.application.query.article;

import com.abservice.application.query.article.model.ArticleTagView;
import com.abservice.application.query.article.model.ArticleView;
import com.abservice.infrastructure.persistence.entity.ArticleAlbumReferenceTableRecord;
import com.abservice.infrastructure.persistence.entity.ArticleTableRecord;
import com.abservice.infrastructure.persistence.entity.ArticleTagLinkTableRecord;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 記事エンティティから Read Model（{@link ArticleView}）への変換
 *
 * <p>
 * CQRS の Read 側マッパー。{@code infrastructure.persistence.datasource} が返す
 * {@link ArticleTableRecord} を照会結果 DTO へ平坦化します。ドメインモデルを経由しません。
 * </p>
 *
 * <p>
 * タグを詰めるのは詳細照会（{@link #toDetailView}）だけです。一覧照会はタグを JOIN FETCH しないため、
 * 一覧用の変換（{@link #toView}）でタグに触れると未初期化のコレクションを触ることになります。
 * </p>
 */
final class ArticleViewMapper {

    private ArticleViewMapper() {
    }

    /**
     * エンティティを一覧用の Read Model へ変換します（タグは空）。
     *
     * @param entity
     *            記事エンティティ
     * @return 記事の Read Model
     */
    static ArticleView toView(ArticleTableRecord entity) {
        return toView(entity, List.of());
    }

    /**
     * エンティティを詳細用の Read Model へ変換します（タグを含む）。
     *
     * @param entity
     *            記事エンティティ（タグを JOIN FETCH 済みであること）
     * @return 記事の Read Model
     */
    static ArticleView toDetailView(ArticleTableRecord entity) {
        return toView(entity, toTagViews(entity));
    }

    private static ArticleView toView(ArticleTableRecord entity, List<ArticleTagView> tags) {
        final var reference = Optional.ofNullable(entity.getAlbumReference());
        return new ArticleView(
                entity.getDomainId(),
                entity.getArticleType(),
                reference.map(ArticleAlbumReferenceTableRecord::getAlbumId)
                        .orElse(null),
                entity.getTitle(),
                // NULL-MEANS-EMPTY: 本文はnullを持たない。列がNULLの既存行は空として扱う（V36 以降はNULLを持たない）
                Optional.ofNullable(entity.getBody())
                        .orElse(""),
                entity.getBodyFormat(),
                entity.getIntroShort(),
                entity.getPublishedAt(),
                entity.getUpdatedAtBusiness(),
                Optional.ofNullable(entity.getIsPublic())
                        .orElse(false),
                reference.map(ArticleAlbumReferenceTableRecord::getFormerAlbumId)
                        .orElse(null),
                reference.map(ArticleAlbumReferenceTableRecord::getAlbumReferenceLostAt)
                        .orElse(null),
                reference.map(ArticleAlbumReferenceTableRecord::getAlbumReferenceLostReason)
                        .orElse(null),
                tags);
    }

    /*
     * ORDER-BY-NAME: 中間テーブルの行順は保証されない。表示の並びを安定させるため名前で並べる。
     */
    private static List<ArticleTagView> toTagViews(ArticleTableRecord entity) {
        return entity.getArticleTagLinks().stream()
                .map(ArticleTagLinkTableRecord::getArticleTag)
                .map(tag -> new ArticleTagView(tag.getDomainId(), tag.getName()))
                .sorted(Comparator.comparing(ArticleTagView::name))
                .toList();
    }
}
