package com.abservice.application.port;

import java.io.IOException;
import java.io.InputStream;

/**
 * 検査した不変のスナップショット。URL・外部の可変キーは保持しない。 確定時はopenStreamの実体を保存し、元のアップロードを再取得しない。
 * close前に開いたストリームを閉じる責務も呼び出し側にある。
 */
public interface InspectedAudio extends AutoCloseable {
    FlacMetadata metadata();

    InputStream openStream() throws IOException;

    @Override
    void close() throws IOException;
}
