package com.abservice.infrastructure.audio;

import com.abservice.application.port.PrivateAudioMaintenance;
import com.abservice.application.port.PrivateAudioRegistrations;
import com.abservice.infrastructure.storage.S3PrivateAudioStorage;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/** 有効化時だけ専用資源を構築する。接続先は既存S3設定、資格情報はAWS標準provider chainを使う。 */
@ApplicationScoped
public class PrivateAudioRuntimeProducer {
    private final PrivateAudioConfig config;
    private final PrivateAudioRegistrations registrations;
    private final PrivateAudioMaintenance maintenance;
    private final Vertx vertx;
    private final String publicBucket;
    private final String region;
    private final boolean pathStyle;
    private final Optional<String> endpoint;

    public PrivateAudioRuntimeProducer(PrivateAudioConfig config, PrivateAudioRegistrations registrations,
            PrivateAudioMaintenance maintenance, Vertx vertx,
            @ConfigProperty(name = "abservice.assets.bucket") String publicBucket,
            @ConfigProperty(name = "quarkus.s3.aws.region") String region,
            @ConfigProperty(name = "quarkus.s3.path-style-access") boolean pathStyle,
            @ConfigProperty(name = "quarkus.s3.endpoint-override") Optional<String> endpoint) {
        this.config = config;
        this.registrations = registrations;
        this.maintenance = maintenance;
        this.vertx = vertx;
        this.publicBucket = publicBucket;
        this.region = region;
        this.pathStyle = pathStyle;
        this.endpoint = endpoint;
    }

    @Produces
    @Singleton
    public PrivateAudioRuntime runtime() throws IOException {
        return "true".equals(config.enabled())
                ? enabled()
                : PrivateAudioRuntime.disabled(vertx);
    }

    @SuppressWarnings("PMD.ForbiddenUnusedLambdaParameter") // LIFECYCLE-API: StartupEventは起動時生成を保証する通知引数。
    void start(@Observes StartupEvent event, PrivateAudioRuntime runtime) {
        runtime.start();
    }

    void stop(@Disposes PrivateAudioRuntime runtime) throws Exception {
        runtime.close();
    }

    private PrivateAudioRuntime enabled() throws IOException {
        validate(config);
        return enabled(
                config.bucket()
                        .filter(value -> value.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]"))
                        .filter(value -> Boolean.FALSE.equals(value.equals(publicBucket)))
                        .orElseThrow(
                                () -> new IllegalArgumentException("A separate private audio bucket is required")));
    }

    private PrivateAudioRuntime enabled(String bucket) throws IOException {
        final var credentials = DefaultCredentialsProvider.builder().build();
        try {
            return connected(
                    client(credentials),
                    credentials,
                    bucket);
        } catch (IOException | RuntimeException failure) {
            credentials.close();
            throw failure;
        }
    }

    static void validate(PrivateAudioConfig config) {
        config.temporaryDirectory().filter(value -> Boolean.FALSE.equals(value.isBlank()))
                .orElseThrow(() -> new IllegalArgumentException("Audio temporary directory is required"));
        requireRange(
                config.temporaryBytes(),
                268435456L,
                8589934592L);
        requireRange(
                config.inputTimeout().toMillis(),
                1,
                300000);
        requireRange(
                config.retention().getSeconds(),
                3600,
                604800);
        requireRange(
                config.maintenanceInterval().toMillis(),
                60000,
                86400000);
        requireRange(
                config.downloadUrlExpiry().getSeconds(),
                60,
                3600);
    }

    private static void requireRange(
            long value,
            long minimum,
            long maximum) {
        Optional.of(value)
                .filter(number -> number >= minimum)
                .filter(number -> number <= maximum)
                .orElseThrow(() -> new IllegalArgumentException("Invalid private audio runtime limit"));
    }

    private PrivateAudioRuntime connected(
            S3Client client,
            DefaultCredentialsProvider credentials,
            String bucket)
            throws IOException {
        try {
            return PrivateAudioRuntime.open(
                    vertx,
                    config,
                    registrations,
                    maintenance,
                    new S3PrivateAudioStorage(client, bucket),
                    new StorageOwner(client, credentials));
        } catch (IOException | RuntimeException failure) {
            client.close();
            throw failure;
        }
    }

    private S3Client client(DefaultCredentialsProvider credentials) {
        final var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentials)
                .httpClientBuilder(
                        UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(5))
                                .socketTimeout(Duration.ofSeconds(30)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build());
        return endpoint.map(URI::create)
                .map(builder::endpointOverride)
                .orElse(builder).build();
    }

    private record StorageOwner(S3Client client, DefaultCredentialsProvider credentials) implements AutoCloseable {
        private StorageOwner {
            Objects.requireNonNull(client);
            Objects.requireNonNull(credentials);
        }

        @Override
        public void close() {
            try {
                client.close();
            } finally {
                credentials.close();
            }
        }
    }
}
