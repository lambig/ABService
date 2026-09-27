package com.abservice.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.application.port.FlacMetadata;
import com.abservice.application.port.InspectedAudio;
import com.abservice.application.port.PrivateAudioConflictException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * ローカル/CIのMinIOへ実SDKでPUTする。検査器の代役として合成バイト列を使い、保存側の整合を確認する。
 * FLACデコードの正しさはNativeFlacInspectorIntegrationTestが担当する。実作品は使用しない。
 */
@DisplayName("非公開音源保存の実S3互換HTTP検証")
class S3PrivateAudioStorageIntegrationTest {
    private final String bucket = "audio-storage-test-" + UUID.randomUUID();
    private final UUID audioId = UUID.randomUUID();
    private final URI endpoint = URI.create(System.getProperty("audio.test.endpoint", "http://localhost:9000"));
    private S3Client client;

    @BeforeEach
    void setUp() {
        client = S3Client.builder()
                .endpointOverride(endpoint).forcePathStyle(true).region(Region.US_EAST_1)
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create("minioadmin", "minioadmin123")))
                .httpClientBuilder(
                        UrlConnectionHttpClient.builder()
                                .connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(10)))
                .build();
        client.createBucket(request -> request.bucket(bucket));
    }

    @AfterEach
    void tearDown() {
        try {
            client.listObjectsV2(request -> request.bucket(bucket)).contents()
                    .forEach(object -> client.deleteObject(request -> request.bucket(bucket).key(object.key())));
            client.deleteBucket(request -> request.bucket(bucket));
        } finally {
            client.close();
        }
    }

    @Test
    @DisplayName("大きな合成実体を保存・照合でき、匿名では取得できない")
    void roundTripsWithoutAnonymousAccess() throws Exception {
        final var snapshot = snapshot(new byte[3 * 1024 * 1024]);
        storage().write(audioId, snapshot);
        assertThat(storage().find(audioId)).contains(snapshot.metadata());
        assertThat(client.getObjectAsBytes(request -> request.bucket(bucket).key(key())).asByteArray())
                .isEqualTo(snapshot.bytes());
        try (var http = HttpClient.newHttpClient()) {
            assertThat(
                    http.send(
                            HttpRequest.newBuilder(endpoint.resolve("/" + bucket + "/" + key())).GET().build(),
                            HttpResponse.BodyHandlers.discarding()).statusCode())
                    .isEqualTo(403);
        }
    }

    @Test
    @DisplayName("同じIDへの異なる実体の再書込を拒否し、最初の実体を保持する")
    void rejectsOverwrite() throws Exception {
        final var first = snapshot(new byte[]{1, 2, 3});
        storage().write(audioId, first);
        assertThatThrownBy(() -> storage().write(audioId, snapshot(new byte[]{4, 5, 6})))
                .isInstanceOf(PrivateAudioConflictException.class);
        assertThat(storage().find(audioId)).contains(first.metadata());
        assertThat(client.getObjectAsBytes(request -> request.bucket(bucket).key(key())).asByteArray())
                .isEqualTo(first.bytes());
    }

    @Test
    @DisplayName("並行PUTは一方だけが成功し、他方は競合する")
    void concurrentWritesHaveOneWinner() throws Exception {
        final var first = CompletableFuture.supplyAsync(() -> writeOrConflict(snapshot(new byte[]{1, 2, 3})));
        final var second = CompletableFuture.supplyAsync(() -> writeOrConflict(snapshot(new byte[]{4, 5, 6})));
        assertThat(Stream.of(first, second).map(CompletableFuture::join).toList())
                .containsExactlyInAnyOrder(true, false);
        assertThat(storage().find(audioId)).isPresent();
    }

    @Test
    @DisplayName("送信実体と検査SHA-256が異なればストレージが拒否し、実体を残さない")
    void rejectsMismatchedChecksum() throws Exception {
        final var changed = new SyntheticSnapshot(new byte[]{4, 5, 6}, snapshot(new byte[]{1, 2, 3}).metadata());
        assertThatThrownBy(() -> storage().write(audioId, changed))
                .isInstanceOf(IOException.class).isNotInstanceOf(PrivateAudioConflictException.class);
        assertThat(storage().find(audioId)).isEmpty();
    }

    private boolean writeOrConflict(SyntheticSnapshot snapshot) {
        try {
            storage().write(audioId, snapshot);
            return true;
        } catch (PrivateAudioConflictException failure) {
            return false;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private S3PrivateAudioStorage storage() {
        return new S3PrivateAudioStorage(client, bucket);
    }

    private String key() {
        return "audio/verified/" + audioId + ".flac";
    }

    private static SyntheticSnapshot snapshot(byte[] bytes) {
        try {
            return new SyntheticSnapshot(bytes, new FlacMetadata(
                    bytes.length, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                    44100, 2, 16, 44100));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private record SyntheticSnapshot(byte[] bytes, FlacMetadata metadata) implements InspectedAudio {
        @Override
        public InputStream openStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void close() {
        }
    }
}
