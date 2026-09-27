package com.abservice.application.port;

import java.io.IOException;

/** 非公開音源のIDが既に使用済み、または同時書込と競合したことを表す。 */
public final class PrivateAudioConflictException extends IOException {
    public PrivateAudioConflictException(Throwable cause) {
        super("Private audio storage conflict", cause);
    }
}
