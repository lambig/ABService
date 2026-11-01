package com.abservice.domain.service;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.AlbumArticle;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.CrossAggregateOperation;
import com.abservice.domain.model.vo.article.AlbumReferenceLostReason;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.domain.repository.article.ArticleRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;

/**
 * アルバムの削除を担うドメインサービス
 *
 * <p>
 * アルバムの削除は拒否されない。一方で、当該アルバムを参照していた {@link Article} に副作用が及ぶ——参照が失効し、
 * 公開中だったものは非公開へ戻る。何が起きるかは参照元の状態に依存し、アルバム集約単体では決まらない。本サービスは
 * 参照元を引き、操作オブジェクト（{@link AlbumDeletion}）を組み立てて「何が起きるか」を確定する。
 * </p>
 *
 * <p>
 * 操作オブジェクトは永続化されず識別子も持たない、本サービスの中でだけ意味を持つモデルのためネスト型として置く
 * （{@code docs/DECISIONS.md} 13）。判定はI/Oを伴わない純粋な評価になるため、実行するコマンドと、実行前に問う照会の双方が
 * 同じ判定を使える（#274）。
 * </p>
 *
 * <p>
 * 公開記事を非公開にしてから参照を失効させる順序は操作オブジェクトが所有する。 呼出側は変更後の記事を保存し、同じ判定から応答を組み立てる。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class AlbumDeletionService implements DomainService {

    private final ArticleRepository articleRepository;

    /**
     * 参照元の記事1件に及ぶ副作用
     *
     * @param article
     *            参照元の記事
     * @param losesAlbumReference
     *            アルバム参照が失効するか（参照という概念を持つのはアルバム紹介記事だけ）
     * @param becomesUnpublished
     *            公開中だったために非公開へ戻るか
     */
    public record ArticleEffect(Article article, boolean losesAlbumReference, boolean becomesUnpublished) {
    }

    /**
     * 削除に伴う記事の変更と、その変更前の影響判定。
     *
     * @param article
     *            保存する変更後の記事
     * @param effect
     *            変更前の記事に対する影響
     */
    public record ArticleChange(Article article, ArticleEffect effect) {
    }

    /**
     * アルバムを削除する試み
     *
     * @param albumId
     *            削除対象のアルバムID
     * @param referencingArticles
     *            当該アルバムを参照している記事（該当なしの場合は空）
     */
    @CrossAggregateOperation
    public record AlbumDeletion(Album.Id albumId, List<Article> referencingArticles) {

        /** 取得時点の参照元を固定し、後から対象が変わらないようにする。 */
        public AlbumDeletion {
            referencingArticles = List.copyOf(referencingArticles);
        }

        /**
         * 非公開化を先に行い、同じ時刻で参照を失効させます。保存は行いません。
         *
         * @param now
         *            遷移の時刻
         * @return 変更後の記事と、応答にも使う変更前の影響
         */
        public List<ArticleChange> apply(BusinessDateTime now) {
            return effects().stream()
                    .map(effect -> new ArticleChange(withoutAlbum(effect, now), effect))
                    .toList();
        }

        private static Article withoutAlbum(ArticleEffect effect, BusinessDateTime now) {
            final var unpublished = Optional.of(effect)
                    .filter(ArticleEffect::becomesUnpublished)
                    .map(published -> published.article().unpublish(now))
                    .orElseGet(effect::article);
            return AlbumArticle.from(unpublished)
                    .<Article>map(article -> article.loseAlbumReference(AlbumReferenceLostReason.ALBUM_DELETED, now))
                    .orElse(unpublished);
        }

        /**
         * 参照元の記事それぞれに何が起きるかを返します。
         *
         * @return 参照元ごとの副作用（参照元が無ければ空）
         */
        public List<ArticleEffect> effects() {
            return referencingArticles.stream()
                    .map(AlbumDeletion::effectOf)
                    .toList();
        }

        private static ArticleEffect effectOf(Article article) {
            return new ArticleEffect(
                    article,
                    AlbumArticle.from(article).isPresent(),
                    article.isPublic());
        }
    }

    /**
     * アルバムを削除する試みを組み立てます。
     *
     * @param albumId
     *            削除対象のアルバムID
     * @return 参照元を伴う試み
     */
    public Uni<AlbumDeletion> attempt(Album.Id albumId) {
        return articleRepository.findByAlbumId(albumId)
                .map(referencing -> new AlbumDeletion(albumId, referencing));
    }
}
