package com.abservice.application.port;

import java.io.IOException;

/** 非公開音源のIDが既に使用済みで、書込の不在条件を満たさないことを表す。成否不明の競合は含めない。 */
public final class PrivateAudioConflictException extends IOException {
    public PrivateAudioConflictException(Throwable cause) {
        super("Private audio storage conflict", cause);
    }
}
