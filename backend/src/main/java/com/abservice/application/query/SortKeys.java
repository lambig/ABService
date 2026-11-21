package com.abservice.application.query;

import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.policy.Policy;
import com.abservice.lib.ErrorResult;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * クエリパラメータから並び順を解決する共通処理
 *
 * <p>
 * 受け取るのは未検証の文字列で、集約ごとの {@link SortKey} 列挙に照らして解決する。解決できない値・要求元に許可されていない値は
 * {@link ValidationException}（400）にする。既定へ黙って落とさない。
 * </p>
 */
public final class SortKeys {

    private SortKeys() {
    }

    /**
     * 並び順を解決します。
     *
     * @param candidates
     *            集約が選べるキーの全体
     * @param sort
     *            クエリパラメータで指定されたキー（未指定なら null）
     * @param direction
     *            クエリパラメータで指定された向き（未指定なら null）
     * @param audience
     *            要求元
     * @param <K>
     *            集約ごとのキー型
     * @return 解決済みの並び順。キー未指定なら {@link SortSpec#defaultOrder()} の向きを既定にする
     */
    public static <K extends SortKey> SortSpec resolve(
            K[] candidates,
            @Nullable String sort,
            @Nullable String direction,
            Audience audience) {
        return Policy.<Optional<SortSpec>>of(
                Optional::isPresent,
                () -> new ErrorResult(
                        "sort",
                        "指定された並び順は使用できません（使用できる値: %s）"
                                .formatted(acceptedKeys(candidates, audience)),
                        "SORT_KEY_NOT_USABLE"))
                .verify(
                        Optional.ofNullable(sort)
                                .map(
                                        raw -> Stream.of(candidates)
                                                .filter(candidate -> candidate.parameterValue().equalsIgnoreCase(raw))
                                                .filter(candidate -> candidate.audiences().contains(audience))
                                                .map(key -> new SortSpec(key.property(), key.defaultDirection()))
                                                .findFirst())
                                .orElseGet(() -> Optional.of(SortSpec.defaultOrder())),
                        Optional::orElseThrow)
                .flatMap(
                        key -> Policy.<Optional<SortDirection>>of(
                                Optional::isPresent,
                                () -> new ErrorResult(
                                        "direction",
                                        "指定された並び順の向きは使用できません（使用できる値: %s）"
                                                .formatted(SortDirection.acceptedValues()),
                                        "SORT_DIRECTION_NOT_USABLE"))
                                .verify(
                                        directionValue(direction, key.direction()),
                                        validDirection -> new SortSpec(key.property(), validDirection.orElseThrow())))
                .resolve(ValidationException::new);
    }

    private static Optional<SortDirection> directionValue(@Nullable String direction, SortDirection fallback) {
        return Optional.ofNullable(direction)
                .map(SortDirection::ofParameterValue)
                .orElseGet(() -> Optional.of(fallback));
    }

    private static String acceptedKeys(SortKey[] candidates, Audience audience) {
        return Stream.of(candidates)
                .filter(candidate -> candidate.audiences().contains(audience))
                .map(SortKey::parameterValue)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }
}
