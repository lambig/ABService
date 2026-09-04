package com.abservice.domain.service;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.repository.article.ArticleRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import lombok.AllArgsConstructor;

/**
 * アルバムの非公開化を担うドメインサービス
 *
 * <p>
 * アルバムの非公開化は拒否されない。一方で「公開中の記事が非公開アルバムを参照する」状態を作らないため、当該アルバムを 参照する公開中の
 * {@link Article} は連動して非公開へ戻る。対象は参照元の状態に依存し、アルバム集約単体では決まらない。
 * 本サービスは参照元を引き、操作オブジェクト（{@link AlbumUnpublication}）を組み立てて「何が起きるか」を確定する。
 * </p>
 *
 * <p>
 * 操作オブジェクトは永続化されず識別子も持たない、本サービスの中でだけ意味を持つモデルのためネスト型として置く
 * （{@code docs/DECISIONS.md} 13）。判定はI/Oを伴わない純粋な評価になるため、実行するコマンドと、実行前に問う照会の
 * 双方が同じ判定を使える（#274）。
 * </p>
 *
 * <p>
 * 遷移そのもの（{@code Article#unpublish}）は操作オブジェクトへ移していない。判定を1箇所にすることが目的で、遷移を
 * 構造で閉じるかは別の関心として扱う（#276）。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class AlbumUnpublicationService implements DomainService {

    private final ArticleRepository articleRepository;

    /**
     * アルバムを非公開化する試み
     *
     * @param albumId
     *            非公開化対象のアルバムID
     * @param referencingArticles
     *            当該アルバムを参照している記事（該当なしの場合は空）
     */
    public record AlbumUnpublication(Album.Id albumId, List<Article> referencingArticles) {

        /**
         * 連動して非公開へ戻る記事を返します。
         *
         * @return 公開中の参照元（該当なしの場合は空）
         */
        public List<Article> articlesBecomingUnpublished() {
            return referencingArticles.stream()
                    .filter(Article::isPublic)
                    .toList();
        }
    }

    /**
     * アルバムを非公開化する試みを組み立てます。
     *
     * @param albumId
     *            非公開化対象のアルバムID
     * @return 参照元を伴う試み
     */
    public Uni<AlbumUnpublication> attempt(Album.Id albumId) {
        return articleRepository.findByAlbumId(albumId)
                .map(referencing -> new AlbumUnpublication(albumId, referencing));
    }
}
