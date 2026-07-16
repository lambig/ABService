package com.abservice.domain.exception;

import com.abservice.lib.ErrorResult;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;

/**
 * 値検証エラーを表す例外
 *
 * <p>
 * Value Object やエンティティの生成時に収集した複数の {@link ErrorResult} を集約して保持します。 主に
 * {@code Result.resolve(...)} で失敗（{@code Failure}）を例外化する際に用います。 presentation
 * 層では 400 Bad Request に変換します。
 * </p>
 */
public final class ValidationException extends DomainException {

    private static final String ERROR_CODE = "VALIDATION_ERROR";

    @NonNull
    private final List<ErrorResult> errors;

    /**
     * メッセージとエラーリストを指定して生成します。
     *
     * @param message
     *            人間可読なエラーメッセージ
     * @param errors
     *            検証エラーのリスト
     */
    public ValidationException(@NonNull String message, @NonNull List<ErrorResult> errors) {
        super(ERROR_CODE, message);
        this.errors = List.copyOf(errors);
    }

    /**
     * エラーリストからメッセージを組み立てて生成します。
     *
     * @param errors
     *            検証エラーのリスト
     */
    public ValidationException(@NonNull List<ErrorResult> errors) {
        this(buildMessage(errors), errors);
    }

    private static @NonNull String buildMessage(@NonNull List<ErrorResult> errors) {
        return errors.stream()
                .map(ErrorResult::toString)
                .collect(Collectors.joining(", "));
    }

    /**
     * 集約された検証エラーのリスト（不変）を返します。
     *
     * @return 検証エラーのリスト
     */
    public @NonNull List<ErrorResult> errors() {
        return errors;
    }
}
