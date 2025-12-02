package com.abservice.domain.model.vo.article;

/**
 * 記事種別
 *
 * <p>
 * アルバム紹介記事、通常の記事、ニュース、イベント情報など、 記事の種類を表現する列挙型です。
 * </p>
 */
public enum ArticleType {
    /** アルバム紹介記事 */
    ALBUM,

    /** 通常記事・ブログ記事 */
    NOTE,

    /** ニュース */
    NEWS,

    /** イベント情報 */
    EVENT,

    /** その他 */
    OTHER
}
