package com.abservice.application.query.article;

import com.abservice.application.query.Audience;
import com.abservice.application.query.SortDirection;
import com.abservice.application.query.SortKey;
import java.util.Set;

/**
 * 記事一覧で選べる並び順のキー
 *
 * <p>
 * 並べるのは業務上の意味を持つ項目に限る。監査列（登録日時・更新日時）は記録のための列であり、業務文脈の並び順には使わない。
 * 管理向けは下書きを含み公開日時が null になりうるため、値を持たない行は向きに依らず末尾に置く（並びの組み立ては 読み取り側が行う）。
 * </p>
 */
public enum ArticleSortKey implements SortKey {

    /** 公開日時 */
    PUBLISHED_AT(
            "publishedAt",
            "publishedAt",
            SortDirection.DESC,
            Set.of(Audience.PUBLIC, Audience.ADMIN));

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
