package com.abservice.infrastructure.storage;

import com.abservice.application.port.PresignedUpload;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Predicate;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.identity.spi.AwsSessionCredentialsIdentity;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.utils.SdkAutoCloseable;

/**
 * 署名する資格情報の期限を超えないアップロードURLを生成します。
 *
 * <p>
 * 資格情報を一度だけ解決し、期限の計算と署名の両方で同じ値を使う。SDKにはその要求だけの固定プロバイダを渡すため、
 * 計算と署名の間に更新されても別の資格情報へすり替わらない。次の発行時には元のプロバイダへ再び問い合わせる。
 * </p>
 */
public class S3UploadPresigner implements AutoCloseable {

    /** 署名処理の所要と時刻差に対する余裕。長い停止は署名後の検査でも拒否する。 */
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(5);

    private final S3Presigner presigner;
    private final AwsCredentialsProvider credentialsProvider;
    private final Clock clock;

    S3UploadPresigner(
            S3Presigner presigner,
            AwsCredentialsProvider credentialsProvider,
            Clock clock) {
        this.presigner = presigner;
        this.credentialsProvider = credentialsProvider;
        this.clock = clock;
    }

    /**
     * S3の署名期限と資格情報の残存時間の短い方でURLを発行します。
     * SigV4の日時とX-Amz-Expiresは秒単位なので、SDKが返す小数秒を有効時間へ足しません。
     *
     * @param request
     *            アップロード先と形式
     * @param requestedDuration
     *            設定上の最大有効時間
     * @return 実際の署名期限を含むURL
     */
    public PresignedUpload presign(PutObjectRequest request, Duration requestedDuration) {
        final var credentials = credentialsProvider.resolveCredentials();
        return presign(
                request,
                requestedDuration,
                credentials,
                credentialDeadline(credentials));
    }

    private PresignedUpload presign(
            PutObjectRequest request,
            Duration requestedDuration,
            AwsCredentials credentials,
            Optional<Instant> deadline) {
        final var signed = presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(signatureDuration(requestedDuration, deadline))
                        .putObjectRequest(
                                request.toBuilder()
                                        .overrideConfiguration(
                                                configuration -> configuration.credentialsProvider(
                                                        StaticCredentialsProvider.create(credentials)))
                                        .build())
                        .build());
        return Optional.of(signed.expiration().truncatedTo(ChronoUnit.SECONDS))
                .filter(expiration -> expiration.isAfter(clock.instant()))
                .filter(expiration -> deadline.stream().allMatch(Predicate.not(expiration::isAfter)))
                .map(expiration -> new PresignedUpload(signed.url().toString(), expiration))
                .orElseThrow(() -> new IllegalStateException("Upload credentials expired while signing"));
    }

    private Optional<Instant> credentialDeadline(AwsCredentials credentials) {
        return credentials.expirationTime()
                .or(
                        () -> credentials instanceof AwsSessionCredentialsIdentity
                                ? Optional.of(missingExpiration())
                                : Optional.empty())
                .map(expiration -> expiration.minus(EXPIRY_MARGIN));
    }

    private static Instant missingExpiration() {
        throw new IllegalStateException("Temporary upload credentials must include their expiration");
    }

    private Duration signatureDuration(Duration requested, Optional<Instant> deadline) {
        return Optional.of(
                deadline.map(limit -> Duration.between(clock.instant(), limit))
                        .filter(remaining -> remaining.compareTo(requested) < 0)
                        .orElse(requested)
                        .truncatedTo(ChronoUnit.SECONDS))
                .filter(duration -> duration.compareTo(Duration.ZERO) > 0)
                .orElseThrow(() -> new IllegalStateException("Upload credentials have no remaining validity"));
    }

    @Override
    public void close() {
        presigner.close();
        Optional.of(credentialsProvider)
                .filter(SdkAutoCloseable.class::isInstance)
                .map(SdkAutoCloseable.class::cast)
                .ifPresent(SdkAutoCloseable::close);
    }
}
