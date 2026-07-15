package com.abservice.domain.exception;

import com.abservice.lib.ErrorResult;
import java.util.List;
import org.jspecify.annotations.NonNull;

/**
 * ビジネスルール違反を表す例外
 *
 * <p>
 * 個々の値は妥当だが、集約の状態遷移や不変条件に反する操作を行った場合に用います （例: 非公開にできない状態での非公開化、重複登録など）。
 * presentation 層では 409 Conflict に変換します。
 * </p>
 */
public final class BusinessRuleViolationException extends DomainException {

    private static final String ERROR_CODE = "BUSINESS_RULE_VIOLATION";

    /**
     * メッセージを指定して生成します。
     *
     * @param message
     *            人間可読なエラーメッセージ
     */
    public BusinessRuleViolationException(@NonNull String message) {
        super(ERROR_CODE, message);
    }

    /**
     * メッセージと起因例外を指定して生成します。
     *
     * @param message
     *            人間可読なエラーメッセージ
     * @param cause
     *            起因例外
     */
    public BusinessRuleViolationException(@NonNull String message, @NonNull Throwable cause) {
        super(
                ERROR_CODE,
                message,
                cause);
    }

    /**
     * 検証エラーを最初のエラーメッセージの {@link BusinessRuleViolationException} に変換する標準リゾルバ。
     * {@code resolve(BusinessRuleViolationException::fromErrors)} のようにメソッド参照で使います。
     *
     * @param errors
     *            検証エラー（非空）
     * @return 最初のエラーメッセージを持つ例外
     */
    public static @NonNull BusinessRuleViolationException fromErrors(@NonNull List<ErrorResult> errors) {
        return new BusinessRuleViolationException(errors.getFirst().message());
    }
}
