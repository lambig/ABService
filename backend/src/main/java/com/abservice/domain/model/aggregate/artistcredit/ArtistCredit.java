package com.abservice.domain.model.aggregate.artistcredit;

import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.aggregate.Aggregate;
import com.abservice.domain.model.vo.common.ArtistCreditName;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.With;
import lombok.experimental.Accessors;

/**
 * アーティスト名義集約
 *
 * <p>
 * アーティストの表記名義を管理します。 例: "Foo Bar", "Foo Bar feat. Baz"
 * </p>
 * <p>
 * 現在は小さいエンティティですが、将来的にアーティスト詳細情報が追加される場合は、 完全な集約ルートとして拡張されます。
 * </p>
 */
@With(AccessLevel.PRIVATE)
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ArtistCredit implements Aggregate<ArtistCredit, ArtistCredit.Id> {
    @EqualsAndHashCode.Include
    private final Id id;
    private final ArtistCreditName displayName;
    private final String sortKey;

    /**
     * 表記名を変更
     *
     * @param newDisplayName
     *            新しい表記名
     * @return 更新されたArtistCredit
     */
    public ArtistCredit changeDisplayName(ArtistCreditName newDisplayName) {
        if (newDisplayName == null) {
            throw new IllegalArgumentException("Display name cannot be null");
        }
        return withDisplayName(newDisplayName);
    }

    /**
     * ソートキーを変更
     *
     * @param newSortKey
     *            新しいソートキー（nullの場合はdisplayNameの値を使用）
     * @return 更新されたArtistCredit
     */
    public ArtistCredit changeSortKey(String newSortKey) {
        return withSortKey(newSortKey);
    }

    @Override
    public Id id() {
        return id;
    }

    /**
     * ArtistCredit ID型
     *
     * @param value
     *            ID値（UUIDv7形式の文字列）
     */
    public record Id(String value) implements EntityId<ArtistCredit> {
        public Id {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("ArtistCredit ID cannot be blank");
            }
            if (!EntityId.isValidUuid(value)) {
                throw new IllegalArgumentException("ArtistCredit ID must be a valid UUID: " + value);
            }
        }

        /**
         * UUIDv7を生成してArtistCredit.Idを作成
         */
        public static Id generate() {
            return new Id(EntityId.generateUuidV7());
        }

        /**
         * 文字列からArtistCredit.Idを生成
         */
        public static Id of(String value) {
            return new Id(value);
        }
    }
}
