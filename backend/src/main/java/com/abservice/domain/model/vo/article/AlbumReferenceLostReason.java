package com.abservice.domain.model.vo.article;

/**
 * 記事のアルバム参照が失効した理由
 *
 * <p>
 * 参照先が失われた経緯を機械可読なコードとして残すための列挙型です。表示文言は持たず、利用側が理由から判断します。
 * </p>
 */
public enum AlbumReferenceLostReason {

    /** 参照先のアルバムが削除された */
    ALBUM_DELETED
}
