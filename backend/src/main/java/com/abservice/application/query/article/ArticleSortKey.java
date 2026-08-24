package com.abservice.application.query.article;

import com.abservice.application.query.Audience;
import com.abservice.application.query.SortDirection;
import com.abservice.application.query.SortKey;
import java.util.Set;

/**
 * 記事一覧で選べる並び順のキー
 *
 * <p>
 * 公開向けは公開日時に限り、管理向けには作業順（更新日時・登録日時）を加える。管理向けは下書きを含み公開日時が null
 * になりうるため、値を持たない行は向きに依らず末尾に置く（並びの組み立ては読み取り側が行う）。
 * </p>
 */
public enum ArticleSortKey implements SortKey {

    /** 公開日時 */
    PUBLISHED_AT(
            "publishedAt",
            "publishedAt",
            SortDirection.DESC,
            Set.of(Audience.PUBLIC, Audience.ADMIN)),

    /** 更新日時（管理向けのみ） */
    UPDATED_AT(
            "updatedAt",
            "updatedAt",
            SortDirection.DESC,
            Set.of(Audience.ADMIN)),

    /** 登録日時（管理向けのみ） */
    CREATED_AT(
            "createdAt",
            "createdAt",
            SortDirection.DESC,
            Set.of(Audience.ADMIN));

    private final String parameterValue;
    private final String property;
    private final SortDirection defaultDirection;
    private final Set<Audience> audiences;

    ArticleSortKey(
            String parameterValue,
            String property,
            SortDirection defaultDirection,
            Set<Audience> audiences) {
        this.parameterValue = parameterValue;
        this.property = property;
        this.defaultDirection = defaultDirection;
        this.audiences = audiences;
    }

    @Override
    public String parameterValue() {
        return parameterValue;
    }

    @Override
    public String property() {
        return property;
    }

    @Override
    public SortDirection defaultDirection() {
        return defaultDirection;
    }

    @Override
    public Set<Audience> audiences() {
        return audiences;
    }
}
