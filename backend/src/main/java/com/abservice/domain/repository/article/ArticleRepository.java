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
     * IDで記事を取得し、その行の世代を伴って返す
     *
     * <p>
     * 世代（{@link Revision}）はドメインの語彙ではなく、**更新の契約**である。編集を始めた時点の世代を要求が
     * 持ち込み、保存の直前に読んだ世代と突き合わせることで、途中で別の操作が保存したかどうかを判定する （DECISIONS
     * 30）。業務モデルへ持たせないのは、記事の事実ではないものを集約の状態に混ぜないためである。
     * </p>
     *
     * @param id
     *            記事ID
     * @return 記事と行の世代。存在しない場合はnull
     */
    Uni<Revisioned> findByIdWithRevision(Article.Id id);

    /**
     * 保存し、保存後の行の世代を伴って返す
     *
     * <p>
     * 保存で世代は進む。呼び出し元が次の更新の条件として返せるようにするため、進んだ後の値を伴わせる
     * （進み方を呼び出し元が推測すると、実装の都合が契約になる）。
     * </p>
     *
     * @param aggregate
     *            保存する記事
     * @return 保存後の記事と行の世代
     */
    Uni<Revisioned> saveWithRevision(Article aggregate);

    /**
     * 行の世代。更新の条件として運ぶだけの値で、業務上の意味を持たない
     *
     * @param value
     *            世代（保存のたびに進む）
     */
    record Revision(int value) {
    }

    /**
     * 記事と、その行の世代の組
     *
     * @param article
     *            記事
     * @param revision
     *            行の世代
     */
    record Revisioned(Article article, Revision revision) {
    }

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
     * <p>
     * 1つのアルバムは複数の記事から参照されうる（アルバムと記事は 1 : 0..N）。アルバムの非公開化・削除に伴う
     * カスケードは、参照している記事すべてを対象にする。
     * </p>
     *
     * @param albumId
     *            アルバムID
     * @return 該当する記事のリスト（該当なしの場合は空）
     */
    Uni<List<Article>> findByAlbumId(Album.Id albumId);

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
