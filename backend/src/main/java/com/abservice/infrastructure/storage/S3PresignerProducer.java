package com.abservice.infrastructure.storage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.net.URI;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 署名付きURL生成器（{@link S3Presigner}）のCDIプロデューサ
 *
 * <p>
 * quarkus-amazon-s3 拡張は S3 クライアントのみを CDI に提供し、presigner は提供しないため自前で組み立てる。設定は
 * 拡張と同じ {@code quarkus.s3.*} を読み、接続先・資格情報の指定を1か所に保つ。資格情報は静的キーの指定が あればそれを使い（開発の
 * MinIO）、無ければ既定のプロバイダ連鎖（本番のインスタンスプロファイル）に委ねる。
 * </p>
 */
@ApplicationScoped
public class S3PresignerProducer {

    private static final String REGION = "quarkus.s3.aws.region";
    private static final String PATH_STYLE_ACCESS = "quarkus.s3.path-style-access";
    private static final String ENDPOINT_OVERRIDE = "quarkus.s3.endpoint-override";
    private static final String ACCESS_KEY_ID = "quarkus.s3.aws.credentials.static-provider.access-key-id";
    private static final String SECRET_ACCESS_KEY = "quarkus.s3.aws.credentials.static-provider.secret-access-key";

    private final String region;
    private final boolean pathStyleAccess;
    private final Optional<String> endpointOverride;
    private final Optional<String> accessKeyId;
    private final Optional<String> secretAccessKey;

    /**
     * @param region
     *            リージョン（{@code quarkus.s3.aws.region}）
     * @param pathStyleAccess
     *            パススタイルアクセスを使うか（{@code quarkus.s3.path-style-access}）
     * @param endpointOverride
     *            接続先の上書き（{@code quarkus.s3.endpoint-override}。本番は未設定）
     * @param accessKeyId
     *            静的アクセスキーID（{@code quarkus.s3.aws.credentials.static-provider.access-key-id}）
     * @param secretAccessKey
     *            静的シークレットキー（{@code quarkus.s3.aws.credentials.static-provider.secret-access-key}）
     */
    public S3PresignerProducer(
            @ConfigProperty(name = REGION) String region,
            @ConfigProperty(name = PATH_STYLE_ACCESS) boolean pathStyleAccess,
            @ConfigProperty(name = ENDPOINT_OVERRIDE) Optional<String> endpointOverride,
            @ConfigProperty(name = ACCESS_KEY_ID) Optional<String> accessKeyId,
            @ConfigProperty(name = SECRET_ACCESS_KEY) Optional<String> secretAccessKey) {
        this.region = region;
        this.pathStyleAccess = pathStyleAccess;
        this.endpointOverride = endpointOverride;
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
    }

    /**
     * 署名付きURL生成器を提供します。
     *
     * @return 設定に従って構成した presigner
     */
    @Produces
    @ApplicationScoped
    public S3Presigner presigner() {
        final var builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(pathStyleAccess)
                                .build());
        return endpointOverride
                .map(URI::create)
                .map(builder::endpointOverride)
                .orElse(builder)
                .build();
    }

    private AwsCredentialsProvider credentialsProvider() {
        return accessKeyId
                .flatMap(keyId -> secretAccessKey.map(secret -> staticProvider(keyId, secret)))
                .orElseGet(() -> DefaultCredentialsProvider.builder().build());
    }

    private static AwsCredentialsProvider staticProvider(String keyId, String secret) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(keyId, secret));
    }
}
