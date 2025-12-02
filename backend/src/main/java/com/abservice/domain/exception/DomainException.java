package com.abservice.domain.exception;

/**
 * ドメイン層の例外基底クラス
 *
 * <p>ビジネスルール違反やドメイン制約違反を表現する例外です。</p>
 */
public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
