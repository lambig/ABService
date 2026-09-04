package com.abservice.application.query.album.model;

/**
 * アルバムの削除が参照元の記事1件に及ぼす影響の Read Model
 *
 * @param articleId
 *            記事のドメインID
 * @param title
 *            記事のタイトル
 * @param losesAlbumReference
 *            アルバム参照が失効するか（参照という概念を持つのはアルバム紹介記事だけ）
 * @param becomesUnpublished
 *            公開中だったために非公開へ戻るか
 */
public record DeletionEffectView(
        String articleId,
        String title,
        boolean losesAlbumReference,
        boolean becomesUnpublished) {
}
