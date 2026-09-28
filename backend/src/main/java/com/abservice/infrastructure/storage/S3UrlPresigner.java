package com.abservice.infrastructure.storage;

import com.abservice.application.port.PresignedDownload;
import com.abservice.application.port.PresignedUpload;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.awscore.presigner.PresignedRequest;
import software.amazon.awssdk.identity.spi.AwsSessionCredentialsIdentity;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * 署名する資格情報の期限を超えないアップロード／取得URLを生成します。
 *
 * <p>
 * 資格情報を一度だけ解決し、期限の計算と署名の両方で同じ値を使う。SDKにはその要求だけの固定プロバイダを渡すため、
 * 計算と署名の間に更新されても別の資格情報へすり替わらない。次の発行時には元のプロバイダへ再び問い合わせる。 PUT と GET
 * で資格情報の固定と期限の計算を共有し、署名する操作だけが異なる。呼出元が渡す絶対の上限も資格情報の期限と同じ経路で扱い、 署名後に絶対時刻で検証する。
 * </p>
 */
public class S3UrlPresigner implements AutoCloseable {

    /** 署名処理の所要と時刻差に対する余裕。長い停止は署名後の検査でも拒否する。 */
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(5);

    private final S3Presigner presigner;
    private final AwsCredentialsProvider credentialsProvider;
    private final Clock clock;

    S3UrlPresigner(
            S3Presigner presigner,
            AwsCredentialsProvider credentialsProvider,
            Clock clock) {
        this.presigner = presigner;
        this.credentialsProvider = credentialsProvider;
        this.clock = clock;
    }

    /**
     * 接続設定に従って署名器を組み立てます。資格情報プロバイダの寿命は呼出元が管理します。
     *
     * @param credentials
     *            署名に使う資格情報プロバイダ
     * @param region
     *            リージョン
     * @param pathStyleAccess
     *            パススタイルアクセスを使うか
     * @param endpointOverride
     *            接続先の上書き（本番は未設定）
     * @return 設定に従って構成した署名器
     */
    public static S3UrlPresigner configured(
            AwsCredentialsProvider credentials,
            String region,
            boolean pathStyleAccess,
            Optional<String> endpointOverride) {
        final var builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentials)
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(pathStyleAccess)
                                .build());
        return new S3UrlPresigner(
                endpointOverride
                        .map(URI::create)
                        .map(builder::endpointOverride)
                        .orElse(builder)
                        .build(),
                credentials,
                Clock.systemUTC());
    }

    /**
     * S3の署名期限と資格情報の残存時間の短い方でアップロードURLを発行します。
     * SigV4の日時とX-Amz-Expiresは秒単位なので、SDKが返す小数秒を有効時間へ足しません。
     *
     * @param request
     *            アップロード先と形式
     * @param requestedDuration
     *            設定上の最大有効時間
     * @return 実際の署名期限を含むURL
     */
    public PresignedUpload presignUpload(PutObjectRequest request, Duration requestedDuration) {
        final var credentials = credentialsProvider.resolveCredentials();
        final var deadline = credentialDeadline(credentials);
        return validated(
                presigner.presignPutObject(
                        PutObjectPresignRequest.builder()
                                .signatureDuration(signatureDuration(requestedDuration, deadline))
                                .putObjectRequest(
                                        request.toBuilder()
                                                .overrideConfiguration(fixedTo(credentials))
                                                .build())
                                .build()),
                deadline,
                PresignedUpload::new);
    }

    /**
     * S3の署名期限・資格情報の残存時間・呼出元の上限のうち最も短いもので取得URLを発行します。実体の存在は確認しません。
     *
     * @param request
     *            取得するオブジェクト
     * @param requestedDuration
     *            設定上の最大有効時間
     * @param notAfter
     *            この時刻を超えてURLを有効にしない絶対の上限（無ければ資格情報の期限だけで決める）
     * @return 実際の署名期限を含むURL
     */
    public PresignedDownload presignDownload(
            GetObjectRequest request,
            Duration requestedDuration,
            Optional<Instant> notAfter) {
        final var credentials = credentialsProvider.resolveCredentials();
        final var deadline = earliest(credentialDeadline(credentials), notAfter);
        return validated(
                presigner.presignGetObject(
                        GetObjectPresignRequest.builder()
                                .signatureDuration(signatureDuration(requestedDuration, deadline))
                                .getObjectRequest(
                                        request.toBuilder()
                                                .overrideConfiguration(fixedTo(credentials))
                                                .build())
                                .build()),
                deadline,
                PresignedDownload::new);
    }

    private static Consumer<AwsRequestOverrideConfiguration.Builder> fixedTo(AwsCredentials credentials) {
        return configuration -> configuration.credentialsProvider(StaticCredentialsProvider.create(credentials));
    }

    private static Optional<Instant> earliest(Optional<Instant> first, Optional<Instant> second) {
        return Stream.concat(first.stream(), second.stream())
                .min(Instant::compareTo);
    }

    private <T> T validated(
            PresignedRequest signed,
            Optional<Instant> deadline,
            BiFunction<String, Instant, T> presigned) {
        return Optional.of(signed.expiration().truncatedTo(ChronoUnit.SECONDS))
                .filter(expiration -> expiration.isAfter(clock.instant()))
                .filter(expiration -> deadline.stream().allMatch(Predicate.not(expiration::isAfter)))
                .map(expiration -> presigned.apply(signed.url().toString(), expiration))
                .orElseThrow(() -> new IllegalStateException("Signing credentials expired while signing"));
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
        throw new IllegalStateException("Temporary signing credentials must include their expiration");
    }

    private Duration signatureDuration(Duration requested, Optional<Instant> deadline) {
        return Optional.of(
                deadline.map(limit -> Duration.between(clock.instant(), limit))
                        .filter(remaining -> remaining.compareTo(requested) < 0)
                        .orElse(requested)
                        .truncatedTo(ChronoUnit.SECONDS))
                .filter(duration -> duration.compareTo(Duration.ZERO) > 0)
                .orElseThrow(() -> new IllegalStateException("Signing credentials have no remaining validity"));
    }

    @Override
    public void close() {
        presigner.close();
    }
}
