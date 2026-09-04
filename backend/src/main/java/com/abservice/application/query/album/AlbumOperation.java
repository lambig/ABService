package com.abservice.application.query.album;

import com.abservice.domain.exception.ValidationException;
import com.abservice.lib.ErrorResult;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 前提を問える、アルバムに対する破壊的操作
 *
 * <p>
 * 許可値を列挙で閉じる。許可外の綴りは既定へ落とさず {@link ValidationException}（400）にする（一覧の並び順の
 * 許可値と同じ流儀）。
 * </p>
 */
public enum AlbumOperation {

    /** 削除。参照していた記事の参照が失効し、公開中だったものは非公開へ戻る */
    DELETE,

    /** 非公開化。参照している公開中の記事が連動して非公開へ戻る */
    UNPUBLISH;

    /** クエリパラメータでの綴り（小文字） */
    private String parameterValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * クエリパラメータの値を操作へ解決します。
     *
     * @param value
     *            クエリパラメータの値
     * @return 該当する操作
     * @throws ValidationException
     *             許可値でない場合
     */
    public static AlbumOperation ofParameterValue(String value) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.parameterValue().equals(value))
                .findFirst()
                .orElseThrow(() -> unusableOperation(value));
    }

    /**
     * 必須のクエリパラメータとして解決します。
     *
     * @param value
     *            クエリパラメータの値（未指定の場合は null）
     * @return 該当する操作
     * @throws ValidationException
     *             未指定または許可値でない場合
     */
    public static AlbumOperation required(Optional<String> value) {
        return value
                .map(AlbumOperation::ofParameterValue)
                .orElseThrow(AlbumOperation::missingOperation);
    }

    private static ValidationException unusableOperation(String value) {
        return new ValidationException(
                List.of(
                        new ErrorResult(
                                "operation",
                                "指定された操作は前提を問えません（使用できる値: %s）".formatted(acceptedValues()),
                                "ALBUM_OPERATION_NOT_USABLE")));
    }

    private static ValidationException missingOperation() {
        return new ValidationException(
                List.of(
                        new ErrorResult(
                                "operation",
                                "前提を問う操作の指定が必要です（使用できる値: %s）".formatted(acceptedValues()),
                                "ALBUM_OPERATION_REQUIRED")));
    }

    private static String acceptedValues() {
        return Arrays.stream(values())
                .map(AlbumOperation::parameterValue)
                .collect(Collectors.joining(", "));
    }
}
