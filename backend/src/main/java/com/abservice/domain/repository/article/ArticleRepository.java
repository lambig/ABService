package com.abservice.domain.repository.article;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.domain.repository.Repository;
import io.smallrye.mutiny.Uni;

import java.util.List;

/**
 * 記事リポジトリ
 *
 * <p>
 * Article集約の永続化と取得を担当します。
 * </p>
 */
public interface ArticleRepository extends Repository<Article, Article.Id> {

    /**
     * 記事タイプで記事を検索
     *
     * @param articleType
     *            記事タイプ
     * @return 該当する記事のリスト
     */
    Uni<List<Article>> findByArticleType(ArticleType articleType);

    /**
     * アルバムIDで記事を検索
     *
     * @param albumId
     *            アルバムID
     * @return 該当する記事、存在しない場合はnull
     */
    Uni<Article> findByAlbumId(Album.Id albumId);

    /**
     * 公開フラグで記事を検索
     *
     * @param publicFlag
     *            公開フラグ
     * @return 該当する記事のリスト
     */
    Uni<List<Article>> findByPublicFlag(boolean publicFlag);

    /**
     * 公開日の範囲で記事を検索
     *
     * @param startDate
     *            開始日時
     * @param endDate
     *            終了日時
     * @return 該当する記事のリスト
     */
    Uni<List<Article>> findByPublishedAtBetween(BusinessDateTime startDate, BusinessDateTime endDate);

    /**
     * タイトルで記事を検索（部分一致）
     *
     * @param titleKeyword
     *            タイトルキーワード
     * @return 該当する記事のリスト
     */
    Uni<List<Article>> findByTitleContaining(String titleKeyword);
}
