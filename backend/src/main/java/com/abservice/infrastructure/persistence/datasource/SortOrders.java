package com.abservice.infrastructure.persistence.datasource;

import com.abservice.application.query.SortDirection;
import com.abservice.application.query.SortSpec;
import io.quarkus.panache.common.Sort;
import java.util.Optional;

/**
 * 解決済みの並び順（{@link SortSpec}）から Panache の {@link Sort} を組み立てる
 *
 * <p>
 * 同値のときの順序を固定するため、キーの後ろに常に {@link SortSpec#DEFAULT_PROPERTY} を第2キーとして加える（キー自身が
 * それである場合は重ねない）。値を持たない行の位置は向きに依らず末尾に固定する（公開日時は下書きで、カタログナンバーは未付与で null になりうる）。
 * </p>
 */
public final class SortOrders {

    private SortOrders() {
    }

    /**
     * 並び順を Panache の {@link Sort} へ変換します。
     *
     * @param spec
     *            解決済みの並び順
     * @return タイブレークを含む Sort
     */
    public static Sort of(SortSpec spec) {
        return Optional.of(spec)
                .filter(SortOrders::isTiebreakItself)
                .map(SortOrders::tiebreakOnly)
                .orElseGet(() -> keyThenTiebreak(spec));
    }

    private static boolean isTiebreakItself(SortSpec spec) {
        return SortSpec.DEFAULT_PROPERTY.equals(spec.property());
    }

    private static Sort tiebreakOnly(SortSpec spec) {
        return Sort.by(
                SortSpec.DEFAULT_PROPERTY,
                toDirection(spec.direction()),
                Sort.NullPrecedence.NULLS_LAST);
    }

    private static Sort keyThenTiebreak(SortSpec spec) {
        return Sort.by(
                spec.property(),
                toDirection(spec.direction()),
                Sort.NullPrecedence.NULLS_LAST)
                .and(
                        SortSpec.DEFAULT_PROPERTY,
                        toDirection(spec.direction()),
                        Sort.NullPrecedence.NULLS_LAST);
    }

    private static Sort.Direction toDirection(SortDirection direction) {
        return switch (direction) {
            case ASC -> Sort.Direction.Ascending;
            case DESC -> Sort.Direction.Descending;
        };
    }
}
