package com.abservice.infrastructure.storage;

import com.abservice.application.port.PresignedDownload;
import com.abservice.application.port.PrivateAudioDownloads;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

/**
 * 専用バケットの確定済み実体を指す署名付きGET URLを発行する。
 *
 * <p>
 * 署名は保存側と同じ資格情報（AWS標準の provider chain）で行い、公開画像用の署名器は使わない。保存と取得の資格情報が
 * 分かれた環境で、保存は成功するのに取得URLだけ別の資格情報で署名されて拒まれることを防ぐ。有効時間は設定値を上限とし、
 * 呼出元が渡す絶対の上限と署名資格情報の残存時間で短縮する。署名はネットワークを使わず、実体の存在は確認しない。 構築と寿命は private audio
 * の実行入口が管理する。
 * </p>
 */
public final class S3PrivateAudioDownloads implements PrivateAudioDownloads, AutoCloseable {
    private final S3UrlPresigner presigner;
    private final AutoCloseable credentials;
    private final String bucket;
    private final Duration expiry;

    /**
     * @param presigner
     *            private audio の資格情報で署名する署名器
     * @param credentials
     *            署名器が使う資格情報プロバイダ（終了時に閉じる）
     * @param bucket
     *            専用バケット
     * @param expiry
     *            取得URLの最大有効時間
     */
    public S3PrivateAudioDownloads(
            S3UrlPresigner presigner,
            AutoCloseable credentials,
            String bucket,
            Duration expiry) {
        this.presigner = Objects.requireNonNull(presigner);
        this.credentials = Objects.requireNonNull(credentials);
        this.bucket = Objects.requireNonNull(bucket);
        this.expiry = Objects.requireNonNull(expiry);
    }

    @Override
    public PresignedDownload presign(UUID audioId, Instant notAfter) {
        return presigner.presignDownload(
                GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(S3PrivateAudioStorage.verifiedKey(audioId))
                        .responseContentType("audio/flac")
                        .build(),
                expiry,
                Optional.of(notAfter));
    }

    @Override
    public void close() throws Exception {
        try {
            presigner.close();
        } finally {
            credentials.close();
        }
    }
}
