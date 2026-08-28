package com.abservice.application.query.album;

import com.abservice.application.query.Audience;
import com.abservice.application.query.SortDirection;
import com.abservice.application.query.SortKey;
import java.util.Set;

/**
 * アルバム一覧で選べる並び順のキー
 *
 * <p>
 * 並べるのは作品としての意味を持つ項目（リリース日・公開日時・カタログナンバー）に限る。監査列（登録日時・更新日時）は
 * 記録のための列であり、業務文脈の並び順には使わない。カタログナンバーはリリース日と実質的に同じ順序になるが、
 * 同一リリース日の作品を番号で並べたい場合に使う。
 * </p>
 */
public enum AlbumSortKey implements SortKey {

    /** リリース日 */
    RELEASE_DATE(
            "releaseDate",
            "releaseDate",
            SortDirection.DESC,
            Set.of(Audience.PUBLIC, Audience.ADMIN)),

    /** 公開日時 */
    PUBLISHED_AT(
            "publishedAt",
            "publishedAt",
            SortDirection.DESC,
            Set.of(Audience.PUBLIC, Audience.ADMIN)),

    /** カタログナンバー */
    CATALOG_NUMBER(
            "catalogNumber",
            "catalogNumber",
            SortDirection.DESC,
            Set.of(Audience.PUBLIC, Audience.ADMIN));

    private final String parameterValue;
    private final String property;
    private final SortDirection defaultDirection;
    private final Set<Audience> audiences;

    AlbumSortKey(
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
