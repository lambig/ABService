package com.abservice.application.query.album.model;

/**
 * アルバムの非公開化が連動して非公開へ戻す記事の Read Model
 *
 * <p>
 * 一覧に載ること自体が「非公開へ戻る」を意味するため、真偽値の項目を持たない。削除の場合は参照の失効と非公開化が
 * 別に起こりうるため、{@link DeletionEffectView} が別の形を持つ。
 * </p>
 *
 * @param articleId
 *            記事のドメインID
 * @param title
 *            記事のタイトル
 */
public record UnpublicationEffectView(String articleId, String title) {
}
