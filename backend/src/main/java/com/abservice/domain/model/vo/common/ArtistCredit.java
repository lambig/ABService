package com.abservice.domain.model.vo.common;

import com.abservice.domain.model.vo.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * アーティスト名義 Value Object
 *
 * <p>
 * アーティストの表記名義を表すValue Objectです。 例: "Foo Bar", "Foo Bar feat. Baz"
 * </p>
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
public class ArtistCredit implements ValueObject<ArtistCredit> {
    private final ArtistCreditName displayName;
    private final String sortKey;

    @Override
    public boolean equivalentTo(ArtistCredit other) {
        return java.util.Optional.ofNullable(other).filter(
                o -> this.displayName.equivalentTo(o.displayName) && java.util.Objects.equals(this.sortKey, o.sortKey))
                .isPresent();
    }

    /**
     * コンストラクタ
     *
     * @param displayName
     *            表示名（必須）
     * @param sortKey
     *            ソートキー（nullの場合はdisplayNameの値を使用）
     */
    public ArtistCredit(ArtistCreditName displayName, String sortKey) {
        if (displayName == null) {
            throw new IllegalArgumentException("Display name cannot be null");
        }
        this.displayName = displayName;
        this.sortKey = sortKey != null ? sortKey : displayName.value();
    }

    /**
     * 表示名のみで生成（ソートキーは表示名と同じ）
     *
     * @param displayName
     *            表示名
     * @return ArtistCredit
     */
    public static ArtistCredit of(String displayName) {
        return new ArtistCredit(new ArtistCreditName(displayName), null);
    }

    /**
     * 表示名とソートキーを指定して生成
     *
     * @param displayName
     *            表示名
     * @param sortKey
     *            ソートキー
     * @return ArtistCredit
     */
    public static ArtistCredit of(String displayName, String sortKey) {
        return new ArtistCredit(new ArtistCreditName(displayName), sortKey);
    }
}
