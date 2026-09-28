package com.abservice.infrastructure.storage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

/**
 * 公開画像のアップロードURLに使う署名器のCDIプロデューサ
 *
 * <p>
 * quarkus-amazon-s3 拡張は S3 クライアントのみを CDI に提供し、presigner は提供しないため自前で組み立てる。設定は
 * 拡張と同じ {@code quarkus.s3.*} を読み、接続先・資格情報の指定を1か所に保つ。資格情報は静的キーの指定が あればそれを使い（開発の
 * MinIO）、無ければ既定のプロバイダ連鎖に委ねる。一時資格情報は期限を返せるプロバイダを使う。 private audio
 * の取得URLは保存側と同じ資格情報で署名するため、この署名器を使わない。
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
    public S3UrlPresigner presigner() {
        return S3UrlPresigner.configured(
                credentialsProvider(),
                region,
                pathStyleAccess,
                endpointOverride);
    }

    /**
     * アプリケーション終了時に署名器を閉じます。
     *
     * @param presigner
     *            終了する署名器
     */
    public void close(@Disposes S3UrlPresigner presigner) {
        presigner.close();
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
