package com.abservice.infrastructure.storage;

import com.abservice.application.port.FlacMetadata;
import com.abservice.application.port.InspectedAudio;
import com.abservice.application.port.PrivateAudioConflictException;
import com.abservice.application.port.PrivateAudioStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ChecksumMode;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * 検査済みの実体だけを専用バケットへ保存する。CDI/APIへの接続は確定フロー側で行う。 バケットは公開画像/CDNから分離し、Block Public
 * AccessとIAMでアクセスを制限する必要がある。 本クラスはバケットのアクセス方針を変更せず、公開URL・ACL・削除・上書き操作を提供しない。
 * クライアントの寿命と接続/ソケットタイムアウトは構築側が管理する。
 */
public final class S3PrivateAudioStorage implements PrivateAudioStorage {
    private static final Duration CALL_TIMEOUT = Duration.ofMinutes(2);
    private static final int PRECONDITION_FAILED = 412;
    private final S3Client s3;
    private final String bucket;

    public S3PrivateAudioStorage(S3Client s3, String bucket) {
        this.s3 = Objects.requireNonNull(s3);
        this.bucket = Objects.requireNonNull(bucket);
    }

    @Override
    @SuppressWarnings("PMD.SingleUseLocalVariable") // RESOURCE-CLOSE: contentは暗黙のcloseでも使用する。
    public void write(UUID audioId, InspectedAudio snapshot) throws IOException {
        try (var content = new SnapshotContent(snapshot)) {
            s3.putObject(
                    writeRequest(audioId, snapshot.metadata()),
                    RequestBody.fromContentProvider(
                            content,
                            snapshot.metadata().byteLength(),
                            "audio/flac"));
        } catch (S3Exception failure) {
            throw failure.statusCode() == PRECONDITION_FAILED
                    ? new PrivateAudioConflictException(failure)
                    : new IOException("Private audio write failed; reconcile before retry", failure);
        } catch (SdkException | UncheckedIOException failure) {
            throw new IOException("Private audio write failed; reconcile before retry", failure);
        }
    }

    @Override
    public Optional<FlacMetadata> find(UUID audioId) throws IOException {
        try {
            return Optional.of(
                    PrivateAudioObjectMetadata.decode(
                            s3.headObject(
                                    HeadObjectRequest.builder()
                                            .bucket(bucket).key(key(audioId))
                                            .checksumMode(ChecksumMode.ENABLED)
                                            .overrideConfiguration(config -> config.apiCallTimeout(CALL_TIMEOUT))
                                            .build())));
        } catch (S3Exception failure) {
            return missingOrFailure(failure);
        } catch (SdkException failure) {
            throw new IOException("Private audio lookup failed", failure);
        }
    }

    private PutObjectRequest writeRequest(UUID audioId, FlacMetadata metadata) throws IOException {
        return PutObjectRequest.builder()
                .bucket(bucket).key(key(audioId))
                .metadata(PrivateAudioObjectMetadata.encode(metadata))
                .contentType("audio/flac").contentLength(metadata.byteLength())
                .checksumSHA256(PrivateAudioObjectMetadata.checksum(metadata))
                .ifNoneMatch("*")
                .overrideConfiguration(config -> config.apiCallTimeout(CALL_TIMEOUT))
                .build();
    }

    private static Optional<FlacMetadata> missingOrFailure(S3Exception failure) throws IOException {
        Optional.of(failure)
                .filter(value -> value.statusCode() == 404)
                .orElseThrow(() -> new IOException("Private audio lookup failed", failure));
        return Optional.empty();
    }

    private static String key(UUID audioId) {
        return "audio/verified/" + Objects.requireNonNull(audioId) + ".flac";
    }

    /** SDK再試行でも同じ検査実体を先頭から開く。前回と最終回のストリームを必ず閉じる。 */
    private static final class SnapshotContent implements ContentStreamProvider, AutoCloseable {
        private final InspectedAudio snapshot;
        private final AtomicReference<InputStream> current = new AtomicReference<>(InputStream.nullInputStream());

        private SnapshotContent(InspectedAudio snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public InputStream newStream() {
            try {
                close();
                final var stream = snapshot.openStream();
                current.set(stream);
                return stream;
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }

        @Override
        public void close() throws IOException {
            current.getAndSet(InputStream.nullInputStream()).close();
        }
    }
}
