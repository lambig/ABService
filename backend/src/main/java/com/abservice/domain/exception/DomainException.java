package com.abservice.domain.exception;

import org.jspecify.annotations.NonNull;

/**
 * ドメイン層の例外基底クラス
 *
 * <p>
 * ビジネスルール違反やドメイン制約違反を表現する例外の抽象基底です。 サブクラスはエラーの種別（値検証・リソース未存在・ビジネスルール違反）を表し、
 * それぞれ機械可読な {@code errorCode} を持ちます。HTTPステータスへの変換は presentation 層の
 * ExceptionMapper が担い、ドメイン層自身はHTTPを知りません。
 * </p>
 *
 * @see ValidationException
 * @see EntityNotFoundException
 * @see BusinessRuleViolationException
 */
public abstract class DomainException extends RuntimeException {

    @NonNull
    private final String errorCode;

    /**
     * @param errorCode
     *            機械可読なエラーコード
     * @param message
     *            人間可読なエラーメッセージ
     */
    protected DomainException(@NonNull String errorCode, @NonNull String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * @param errorCode
     *            機械可読なエラーコード
     * @param message
     *            人間可読なエラーメッセージ
     * @param cause
     *            起因例外
     */
    protected DomainException(
            @NonNull String errorCode,
            @NonNull String message,
            @NonNull Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * 機械可読なエラーコードを返します。
     *
     * @return エラーコード
     */
    public @NonNull String errorCode() {
        return errorCode;
    }
}
