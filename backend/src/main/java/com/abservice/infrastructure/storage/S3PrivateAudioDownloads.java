package com.abservice.infrastructure.storage;

import com.abservice.application.port.PresignedDownload;
import com.abservice.application.port.PrivateAudioDownloads;
import com.abservice.infrastructure.audio.PrivateAudioConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

/**
 * 専用バケットの確定済み実体を指す署名付きGET URLを発行する。
 *
 * <p>
 * 有効時間は設定値（{@code abservice.private-audio.download-url-expiry}）を上限とし、呼出元が渡す期限と署名資格情報の
 * 残存時間で短縮する。署名はネットワークを使わず、実体の存在は確認しない。バケットが未設定なら発行できない（機能無効時は 手前で未存在として拒む）。
 * </p>
 */
@ApplicationScoped
public class S3PrivateAudioDownloads implements PrivateAudioDownloads {
    private final S3UrlPresigner presigner;
    private final PrivateAudioConfig config;
    private final Clock clock;

    @Inject
    public S3PrivateAudioDownloads(S3UrlPresigner presigner, PrivateAudioConfig config) {
        this(
                presigner,
                config,
                Clock.systemUTC());
    }

    S3PrivateAudioDownloads(
            S3UrlPresigner presigner,
            PrivateAudioConfig config,
            Clock clock) {
        this.presigner = presigner;
        this.config = config;
        this.clock = clock;
    }

    @Override
    public PresignedDownload presign(UUID audioId, Instant notAfter) {
        return presigner.presignDownload(
                GetObjectRequest.builder()
                        .bucket(bucket())
                        .key(S3PrivateAudioStorage.verifiedKey(audioId))
                        .responseContentType("audio/flac")
                        .build(),
                requestedDuration(notAfter));
    }

    private String bucket() {
        return config.bucket()
                .orElseThrow(() -> new IllegalStateException("A private audio bucket is required to resolve URLs"));
    }

    /** 設定の有効時間と呼出元の期限の短い方。秒未満は切り捨て、残りが無ければ発行しない。 */
    private Duration requestedDuration(Instant notAfter) {
        return Optional.of(Duration.between(clock.instant(), notAfter))
                .filter(remaining -> remaining.compareTo(config.downloadUrlExpiry()) < 0)
                .orElse(config.downloadUrlExpiry())
                .truncatedTo(ChronoUnit.SECONDS);
    }
}
