package com.abservice.presentation.rest.audio;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** 実作品・実資格情報を使わず、専用MinIOバケットとAWS標準chainのテスト値を所有する。 */
public class AudioHttpTestResource implements QuarkusTestResourceLifecycleManager {
    private final String bucket = "audio-http-test-" + UUID.randomUUID();
    private final Optional<String> oldAccess = Optional.ofNullable(System.getProperty("aws.accessKeyId"));
    private final Optional<String> oldSecret = Optional.ofNullable(System.getProperty("aws.secretAccessKey"));
    private S3Client client;

    @Override
    public Map<String, String> start() {
        System.setProperty("aws.accessKeyId", "minioadmin");
        System.setProperty("aws.secretAccessKey", "minioadmin123");
        client = S3Client.builder().endpointOverride(URI.create("http://localhost:9000"))
                .forcePathStyle(true).region(Region.US_EAST_1)
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create("minioadmin", "minioadmin123")))
                .build();
        client.createBucket(request -> request.bucket(bucket));
        return Map.of(
                "abservice.private-audio.enabled",
                "true",
                "abservice.private-audio.bucket",
                bucket,
                "abservice.private-audio.temporary-directory",
                "/tmp/" + bucket,
                "abservice.private-audio.input-timeout",
                "PT15S",
                "abservice.private-audio.maintenance-interval",
                "PT1H");
    }

    @Override
    public void stop() {
        try (var owned = client) {
            owned.listObjectsV2(request -> request.bucket(bucket)).contents()
                    .forEach(object -> owned.deleteObject(request -> request.bucket(bucket).key(object.key())));
            owned.deleteBucket(request -> request.bucket(bucket));
        } finally {
            restore("aws.accessKeyId", oldAccess);
            restore("aws.secretAccessKey", oldSecret);
        }
    }

    private static void restore(String key, Optional<String> value) {
        value.ifPresentOrElse(saved -> System.setProperty(key, saved), () -> System.clearProperty(key));
    }
}
