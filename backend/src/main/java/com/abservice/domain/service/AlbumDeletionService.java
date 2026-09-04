package com.abservice.domain.service;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.AlbumArticle;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.repository.article.ArticleRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
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
 * 遷移そのもの（{@code Article#unpublish} /
 * {@code AlbumArticle#loseAlbumReference}）は操作オブジェクトへ
 * 移していない。判定を1箇所にすることが目的で、遷移を構造で閉じるかは別の関心として扱う（#276）。
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
     * アルバムを削除する試み
     *
     * @param albumId
     *            削除対象のアルバムID
     * @param referencingArticles
     *            当該アルバムを参照している記事（該当なしの場合は空）
     */
    public record AlbumDeletion(Album.Id albumId, List<Article> referencingArticles) {

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
