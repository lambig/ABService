package com.abservice.application.port;

/** 入力音源の検査不合格。I/O障害・検査器不在・時間切れと区別する。 */
public final class InvalidFlacException extends RuntimeException {
    public InvalidFlacException(String code) {
        super(code);
    }
}
