package com.abservice.application.port;

import java.io.IOException;

/** 同時実行枠・登録状態・保存実体の照合が操作の前提を満たさない。通信障害とは区別する。 */
public final class AudioOperationConflictException extends IOException {
    public AudioOperationConflictException() {
        super("Audio operation conflicts with the current state");
    }
}
