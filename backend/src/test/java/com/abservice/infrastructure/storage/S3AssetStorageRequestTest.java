package com.abservice.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.application.port.AssetConfirmConflictException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * 確定が保管先へ送る要求と、保管先の応答の扱いのテスト
 *
 * <p>
 * 確定の正しさはコピー操作自身の条件に置いている。コピー先の条件（{@code If-None-Match}）はローカルの MinIO が
 * 無視するため統合テストでは観測できず、ここで「要求に載せていること」を固定する（#285）。
 * </p>
 */
@DisplayName("S3AssetStorage（確定の要求と応答の扱い）のテスト")
class S3AssetStorageRequestTest {

    private static final String BUCKET = "assets-bucket";

    private static final String PUBLIC_BASE_PATH = "/assets";

    private static final String PENDING_PREFIX = "pending";

    private static final String KEY = "0192f8a0-0000-7000-8000-000000000000.png";

    private static final String ENTITY_TAG = "\"inspected-entity-tag\"";

    private static final List<Integer> CONFLICT_STATUSES = List.of(
            412,
            404,
            409);

    @Test
    @DisplayName("確定はコピー元の一致とコピー先の不在を同じ要求へ載せる")
    void publishSendsSourceAndDestinationConditions() {
        final var s3 = new RecordingS3Client(S3AssetStorageRequestTest::copied, S3AssetStorageRequestTest::deleted);

        storage(s3).publish(KEY, ENTITY_TAG).await().indefinitely();

        assertThat(s3.copyRequests()).singleElement()
                .satisfies(
                        request -> {
                            assertThat(request.copySourceIfMatch()).as("コピー元は検査した実体").isEqualTo(ENTITY_TAG);
                            assertThat(request.ifNoneMatch()).as("コピー先はまだ無い").isEqualTo("*");
                            assertThat(request.sourceKey()).isEqualTo(PENDING_PREFIX + "/" + KEY);
                            assertThat(request.destinationKey()).isEqualTo("assets/" + KEY);
                        });
    }

    @Test
    @DisplayName("確定後の片付けに失敗しても、確定は成功として返る")
    void publishSucceedsEvenWhenDiscardingLeftoverFails() {
        final var s3 = new RecordingS3Client(
                S3AssetStorageRequestTest::copied,
                () -> CompletableFuture.failedFuture(new CompletionException(failure(500))));

        storage(s3).publish(KEY, ENTITY_TAG).await().indefinitely();

        assertThat(s3.copyRequests()).as("実体は配信対象へ移っている").hasSize(1);
    }

    @Test
    @DisplayName("確定の条件を満たせなかった応答は競合として返す")
    void publishTranslatesConditionFailuresIntoConflict() {
        CONFLICT_STATUSES.forEach(
                status -> assertThatThrownBy(() -> publishFailingWith(status))
                        .as("状態コード " + status)
                        .isInstanceOf(AssetConfirmConflictException.class)
                        .hasMessageContaining(KEY));
    }

    @Test
    @DisplayName("確定の条件と関係のない失敗は競合に畳まない")
    void publishKeepsUnrelatedFailures() {
        assertThatThrownBy(() -> publishFailingWith(500))
                .isNotInstanceOf(AssetConfirmConflictException.class);
    }

    private static void publishFailingWith(int statusCode) {
        final var s3 = new RecordingS3Client(
                () -> CompletableFuture.failedFuture(new CompletionException(failure(statusCode))),
                S3AssetStorageRequestTest::deleted);
        storage(s3).publish(KEY, ENTITY_TAG).await().indefinitely();
    }

    private static S3AssetStorage storage(S3AsyncClient s3) {
        return new S3AssetStorage(
                s3,
                null,
                BUCKET,
                PUBLIC_BASE_PATH,
                PENDING_PREFIX,
                Duration.ofMinutes(10));
    }

    private static S3Exception failure(int statusCode) {
        return (S3Exception) S3Exception.builder()
                .statusCode(statusCode)
                .message("status " + statusCode)
                .build();
    }

    private static CompletableFuture<CopyObjectResponse> copied() {
        return CompletableFuture.completedFuture(CopyObjectResponse.builder().build());
    }

    private static CompletableFuture<DeleteObjectResponse> deleted() {
        return CompletableFuture.completedFuture(DeleteObjectResponse.builder().build());
    }

    /**
     * 送られたコピー要求を控える保管先クライアント
     *
     * <p>
     * 確定で使うのはコピーと削除だけ。それ以外の操作は呼ばれれば {@link S3AsyncClient} の既定
     * （{@code UnsupportedOperationException}）で落ちる。
     * </p>
     */
    private static final class RecordingS3Client implements S3AsyncClient {

        private final Supplier<CompletableFuture<CopyObjectResponse>> copyOutcome;
        private final Supplier<CompletableFuture<DeleteObjectResponse>> deleteOutcome;
        private List<CopyObjectRequest> copyRequests = List.of();

        private RecordingS3Client(
                Supplier<CompletableFuture<CopyObjectResponse>> copyOutcome,
                Supplier<CompletableFuture<DeleteObjectResponse>> deleteOutcome) {
            this.copyOutcome = copyOutcome;
            this.deleteOutcome = deleteOutcome;
        }

        @Override
        public CompletableFuture<CopyObjectResponse> copyObject(CopyObjectRequest request) {
            copyRequests = Stream.concat(
                    copyRequests.stream(),
                    Stream.of(request))
                    .toList();
            return copyOutcome.get();
        }

        @Override
        public CompletableFuture<DeleteObjectResponse> deleteObject(DeleteObjectRequest request) {
            return deleteOutcome.get();
        }

        List<CopyObjectRequest> copyRequests() {
            return List.copyOf(copyRequests);
        }

        @Override
        public String serviceName() {
            return S3AsyncClient.SERVICE_NAME;
        }

        @Override
        public void close() {
        }
    }
}
