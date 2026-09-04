package com.abservice.application.query.album;

import com.abservice.application.query.QueryService;
import com.abservice.application.query.album.model.DeletionEffectView;
import com.abservice.application.query.album.model.UnpublicationEffectView;
import java.util.List;

/**
 * アルバムに対する操作の前提を問う照会の結果
 *
 * <p>
 * 「未存在」を例外ではなく正常な結果の一種として型で表現します（sealed）。presentation 層は各バリアントで switch
 * し、{@link Deletion} / {@link Unpublication} を 200、{@link NotFound} を 404
 * に対応づけます。
 * </p>
 *
 * <p>
 * 影響の形を操作ごとに分けています。削除は「参照の失効」と「非公開化」が別に起こりうる一方、非公開化は連動非公開だけが
 * 起きるため、1つの型へ潰すとどちらの操作でも使われない項目が混ざります。
 * </p>
 *
 * <p>
 * 拒否の理由を持つ項目は置いていません。アルバムの削除・非公開化はいずれも業務規則が拒否しないためです（{@code Album}
 * の非公開化に事前条件はなく、削除はべき等）。拒否を伴う操作（参照されているチューンの削除など）を同じ照会へ載せる時点で、 既存の
 * {@code ErrorResult} を使って項目を足します——項目の追加は呼ぶ側を壊しませんが、常に空の項目を先に置くと
 * 呼ぶ側に無駄な分岐を強いるため、この順序を採ります。
 * </p>
 */
public sealed interface GetAlbumPreconditionsResult extends QueryService.Result
        permits GetAlbumPreconditionsResult.Deletion,
        GetAlbumPreconditionsResult.Unpublication,
        GetAlbumPreconditionsResult.NotFound {

    /**
     * 削除の前提（→ 200）
     *
     * @param affectedArticles
     *            削除によって影響を受ける記事（該当なしの場合は空）
     */
    record Deletion(List<DeletionEffectView> affectedArticles) implements GetAlbumPreconditionsResult {
    }

    /**
     * 非公開化の前提（→ 200）
     *
     * @param articlesBecomingUnpublished
     *            連動して非公開へ戻る記事（該当なしの場合は空）
     */
    record Unpublication(List<UnpublicationEffectView> articlesBecomingUnpublished)
            implements
                GetAlbumPreconditionsResult {
    }

    /**
     * アルバムが見つからなかった結果（→ 404）
     */
    record NotFound() implements GetAlbumPreconditionsResult {
    }
}
