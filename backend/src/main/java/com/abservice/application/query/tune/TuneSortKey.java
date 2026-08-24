package com.abservice.application.query.tune;

import com.abservice.application.query.Audience;
import com.abservice.application.query.SortDirection;
import com.abservice.application.query.SortKey;
import java.util.Set;

/**
 * チューン一覧で選べる並び順のキー
 *
 * <p>
 * チューン一覧は認証必須のマスタ系照会のため、管理向けの作業順（更新日時・登録日時）だけを持つ。
 * </p>
 */
public enum TuneSortKey implements SortKey {

    /** 更新日時 */
    UPDATED_AT(
            "updatedAt",
            "updatedAt",
            SortDirection.DESC,
            Set.of(Audience.ADMIN)),

    /** 登録日時 */
    CREATED_AT(
            "createdAt",
            "createdAt",
            SortDirection.DESC,
            Set.of(Audience.ADMIN));

    private final String parameterValue;
    private final String property;
    private final SortDirection defaultDirection;
    private final Set<Audience> audiences;

    TuneSortKey(
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
