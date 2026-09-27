package com.abservice.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.application.port.FlacMetadata;
import com.abservice.application.port.InspectedAudio;
import com.abservice.application.port.PrivateAudioConflictException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ChecksumMode;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@DisplayName("非公開音源の条件付き保存と資源所有権")
class S3PrivateAudioStorageTest {
    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final FlacMetadata METADATA = new FlacMetadata(
            3, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            44100, 2, 16, 44100);

    @Test
    @DisplayName("検査実体とチェックサムを条件付きPUTし、元キーや公開URLを使わない")
    void writesSnapshotWithConditions() throws Exception {
        final var snapshot = new Snapshot();
        final var client = new Client((request, body) -> {
            assertThat(request.bucket()).isEqualTo("private-audio-test");
            assertThat(request.key()).isEqualTo("audio/verified/" + ID + ".flac");
            assertThat(request.ifNoneMatch()).isEqualTo("*");
            assertThat(request.contentLength()).isEqualTo(3L);
            assertThat(request.contentType()).isEqualTo("audio/flac");
            assertThat(request.aclAsString()).isNull();
            assertThat(request.checksumSHA256()).isEqualTo("ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0=");
            assertThat(request.metadata()).containsEntry("audio-schema", "1")
                    .containsEntry("sha256", METADATA.sha256());
            assertThat(request.overrideConfiguration().orElseThrow().apiCallTimeout()).isPresent();
            assertThat(body.optionalContentLength()).contains(3L);
            assertThat(body.contentStreamProvider().newStream()).hasBinaryContent(new byte[]{'a', 'b', 'c'});
        });
        storage(client).write(ID, snapshot);
        assertThat(snapshot.closedStreams.get()).isEqualTo(1);
        assertThat(snapshot.closedSnapshots.get()).isZero();
    }

    @Test
    @DisplayName("SDK再試行は同じ検査実体を開き直し、全ストリームを閉じる")
    void reopensSnapshotForRetry() throws Exception {
        final var snapshot = new Snapshot();
        storage(new Client((request, body) -> {
            assertThat(request.ifNoneMatch()).isEqualTo("*");
            assertThat(body.contentStreamProvider().newStream()).hasBinaryContent(new byte[]{'a', 'b', 'c'});
            assertThat(body.contentStreamProvider().newStream()).hasBinaryContent(new byte[]{'a', 'b', 'c'});
            assertThat(snapshot.closedStreams.get()).isEqualTo(1);
        })).write(ID, snapshot);
        assertThat(snapshot.closedStreams.get()).isEqualTo(2);
        assertThat(snapshot.closedSnapshots.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {409, 412})
    @DisplayName("同時書込と使用済みIDを競合として返し、ストリームを閉じる")
    void rejectsConflictingWrite(int status) {
        final var snapshot = new Snapshot();
        assertThatThrownBy(() -> storage(new Client((request, body) -> {
            assertThat(request.ifNoneMatch()).isEqualTo("*");
            body.contentStreamProvider().newStream();
            throw failure(status);
        })).write(ID, snapshot)).isInstanceOf(PrivateAudioConflictException.class);
        assertThat(snapshot.closedStreams.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("転送障害は競合に変換せず、成否不明として呼出元へ返す")
    void preservesUnknownWriteOutcome() {
        final var snapshot = new Snapshot();
        assertThatThrownBy(() -> storage(new Client((request, body) -> {
            assertThat(request.ifNoneMatch()).isEqualTo("*");
            body.contentStreamProvider().newStream();
            throw SdkClientException.create("test connection loss");
        })).write(ID, snapshot)).isInstanceOf(IOException.class)
                .isNotInstanceOf(PrivateAudioConflictException.class).hasMessageContaining("reconcile");
        assertThat(snapshot.closedStreams.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("照合時はS3チェックサムと実測長を取得し、検査メタデータを復元する")
    void readsMetadataWithChecksum() throws Exception {
        final var client = new Client((request, body) -> {
        });
        assertThat(storage(client).find(ID)).contains(METADATA);
        assertThat(client.head.checksumMode()).isEqualTo(ChecksumMode.ENABLED);
        assertThat(client.head.key()).isEqualTo("audio/verified/" + ID + ".flac");
    }

    @Test
    @DisplayName("未存在だけをemptyとし、403や障害を未登録として扱わない")
    void distinguishesMissingFromLookupFailure() throws Exception {
        final var client = new Client((request, body) -> {
        });
        client.headStatus = 404;
        assertThat(storage(client).find(ID)).isEmpty();
        List.of(403, 500).forEach(status -> {
            client.headStatus = status;
            assertThatThrownBy(() -> storage(client).find(ID)).isInstanceOf(IOException.class);
        });
    }

    @Test
    @DisplayName("欠損・型違い・不正値・チェックサム不一致の保存メタデータを拒否する")
    void rejectsCorruptMetadata() throws Exception {
        final var good = stored();
        final var client = new Client((request, body) -> {
        });
        final var invalid = List.of(
                good.toBuilder().checksumSHA256("wrong").build(),
                good.toBuilder().checksumSHA256((String) null).build(),
                good.toBuilder().contentType("image/png").build(),
                good.toBuilder().contentLength(0L).build(),
                good.toBuilder().metadata(Map.of()).build(),
                good.toBuilder().metadata(Map.of("audio-schema", "2")).build(),
                good.toBuilder().metadata(
                        Map.of(
                                "audio-schema",
                                "1",
                                "sha256",
                                "not-a-hash",
                                "sample-rate",
                                "invalid"))
                        .build());
        invalid.forEach(response -> {
            client.response = response;
            assertThatThrownBy(() -> storage(client).find(ID)).isInstanceOf(IOException.class);
        });
    }

    private static S3PrivateAudioStorage storage(S3Client client) {
        return new S3PrivateAudioStorage(client, "private-audio-test");
    }

    private static HeadObjectResponse stored() throws IOException {
        return HeadObjectResponse.builder().contentType("audio/flac").contentLength(3L)
                .metadata(PrivateAudioObjectMetadata.encode(METADATA))
                .checksumSHA256(PrivateAudioObjectMetadata.checksum(METADATA)).build();
    }

    private static S3Exception failure(int status) {
        return (S3Exception) S3Exception.builder().statusCode(status).build();
    }

    private static final class Client implements S3Client {
        private final BiConsumer<PutObjectRequest, RequestBody> put;
        private HeadObjectRequest head;
        private HeadObjectResponse response;
        private int headStatus = 200;

        private Client(BiConsumer<PutObjectRequest, RequestBody> put) {
            this.put = put;
            try {
                response = stored();
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        }

        @Override
        public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
            put.accept(request, body);
            return PutObjectResponse.builder().build();
        }

        @Override
        public HeadObjectResponse headObject(HeadObjectRequest request) {
            head = request;
            return headStatus == 200
                    ? response
                    : failHead();
        }

        private HeadObjectResponse failHead() {
            throw failure(headStatus);
        }

        @Override
        public String serviceName() {
            return SERVICE_NAME;
        }

        @Override
        public void close() {
        }
    }

    private static final class Snapshot implements InspectedAudio {
        private final AtomicInteger closedStreams = new AtomicInteger();
        private final AtomicInteger closedSnapshots = new AtomicInteger();

        @Override
        public FlacMetadata metadata() {
            return METADATA;
        }

        @Override
        public InputStream openStream() {
            return new ByteArrayInputStream(new byte[]{'a', 'b', 'c'}) {
                @Override
                public void close() {
                    closedStreams.incrementAndGet();
                }
            };
        }

        @Override
        public void close() {
            closedSnapshots.incrementAndGet();
        }
    }
}
