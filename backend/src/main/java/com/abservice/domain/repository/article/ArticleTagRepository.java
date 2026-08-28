package com.abservice.domain.repository.article;

import com.abservice.domain.model.entity.article.ArticleTag;
import io.smallrye.mutiny.Uni;

/**
 * 記事タグリポジトリ
 *
 * <p>
 * 記事タグは記事に属する語彙ではなく、複数の記事が共有する語彙である（{@code article_tag.name} は一意）。同じ名前の
 * タグを二重に作らないため、タグを付ける側は必ずこのリポジトリで既存を引き当ててから使う。
 * </p>
 *
 * <p>
 * 集約ルートではないため永続化の入口は持たない。タグの保存は、そのタグを持つ {@link ArticleRepository} 経由で行う。
 * </p>
 */
public interface ArticleTagRepository {

    /**
     * 名前でタグを取得します。
     *
     * @param name
     *            タグ名
     * @return 該当するタグ。存在しない場合は null
     */
    Uni<ArticleTag> findByName(String name);
}
