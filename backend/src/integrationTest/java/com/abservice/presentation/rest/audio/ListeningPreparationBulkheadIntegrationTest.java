package com.abservice.presentation.rest.audio;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.application.query.audio.GetListeningPreparationService;
import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.mutiny.Uni;
import io.restassured.response.Response;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

/** 実REST/CDIの受付制御を通す。待機するユースケースだけをテスト用に差し替える。 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
@TestProfile(ListeningPreparationBulkheadIntegrationTest.Limits.class)
class ListeningPreparationBulkheadIntegrationTest {
    private static final String PATH = "/api/v1/admin/listening-preparation";
    @Inject
    private ControlledPreparation controlled;

    public static class Limits implements QuarkusTestProfile {
        private static final String KEY = "quarkus.fault-tolerance.\"com.abservice.presentation.rest.audio."
                + "ListeningPreparationAdminQueryResource/preparation\".";
        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(ControlledPreparation.class);
        }

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    KEY + "bulkhead.value",
                    "1",
                    KEY + "bulkhead.waiting-task-queue",
                    "1",
                    KEY + "timeout.value",
                    "3000");
        }
    }

    @AfterEach
    void release() {
        controlled.release();
    }

    @Test
    @DisplayName("独立したHTTP要求の受付枠を共有し、超過を拒否して終了後に再受付する")
    void queuesAcrossRequestsRejectsOverflowAndReusesCapacity() throws Exception {
        authorized().get(PATH).then().statusCode(200);
        controlled.hold();
        try (var executor = Executors.newFixedThreadPool(3)) {
            final var first = CompletableFuture.supplyAsync(() -> authorized().get(PATH), executor);
            controlled.awaitEntry();
            final var second = CompletableFuture.supplyAsync(() -> authorized().get(PATH), executor);
            final var third = CompletableFuture.supplyAsync(() -> authorized().get(PATH), executor);
            final var rejected = CompletableFuture.anyOf(second, third).get(2, TimeUnit.SECONDS);
            assertThat(rejected).isNotNull();
            assertThat(controlled.started()).isEqualTo(1);
            controlled.release();
            final var responses = List.of(
                    first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS),
                    third.get(5, TimeUnit.SECONDS));
            assertThat(responses).extracting(Response::statusCode)
                    .containsExactlyInAnyOrder(
                            200,
                            200,
                            503);
            assertThat(controlled.maximum()).isEqualTo(1);
            responses.stream().filter(response -> response.statusCode() == 503).forEach(response -> {
                assertThat(response.header("Retry-After")).isEqualTo("1");
                assertThat(response.contentType()).contains("application/problem+json");
                assertThat(response.jsonPath().getString("type")).isEqualTo("urn:abservice:error:PREPARATION_BUSY");
            });
        }
        authorized().get(PATH).then().statusCode(200);
    }

    @Test
    @DisplayName("待ち行列を含む期限で応答を終え、処理終了後に再受付する")
    void timesOutIncludingQueueWaitAndAcceptsRequestsAfterWorkEnds() throws Exception {
        // HTTP/CDIの初回初期化を、受付処理そのものの期限計測から分離する。
        authorized().get(PATH).then().statusCode(200);
        controlled.hold();
        try (var executor = Executors.newFixedThreadPool(2)) {
            final var first = CompletableFuture.supplyAsync(() -> authorized().get(PATH), executor);
            controlled.awaitEntry();
            final var queued = CompletableFuture.supplyAsync(() -> authorized().get(PATH), executor);
            CompletableFuture.allOf(first, queued).get(5, TimeUnit.SECONDS);
            assertThat(first.join().statusCode()).isEqualTo(503);
            assertThat(queued.join().statusCode()).isEqualTo(503);
            assertThat(queued.join().header("Retry-After")).isEqualTo("1");
            controlled.release();
        }
        authorized().get(PATH).then().statusCode(200);
    }

    /** テスト専用の待機可能な処理。資格情報や本番のデータを使用しない。 */
    @Alternative
    @ApplicationScoped
    public static class ControlledPreparation extends GetListeningPreparationService {
        private final AtomicReference<CompletableFuture<Void>> gate = new AtomicReference<>(
                CompletableFuture.completedFuture(null));
        private final Semaphore entered = new Semaphore(0);
        private final AtomicInteger started = new AtomicInteger();
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger maximum = new AtomicInteger();

        ControlledPreparation() {
            super(
                    null,
                    null,
                    null);
        }

        int started() {
            return started.get();
        }

        int maximum() {
            return maximum.get();
        }

        void hold() {
            started.set(0);
            maximum.set(0);
            entered.drainPermits();
            gate.set(new CompletableFuture<>());
        }

        void release() {
            gate.get().complete(null);
        }

        void awaitEntry() throws InterruptedException {
            assertThat(entered.tryAcquire(5, TimeUnit.SECONDS)).isTrue();
        }

        @Override
        public Uni<Result> query(Query query) {
            return Uni.createFrom().deferred(() -> {
                started.incrementAndGet();
                maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                entered.release();
                return Uni.createFrom().completionStage(gate.get())
                        .replaceWith(
                                new Result(
                                        List.of(),
                                        0,
                                        1,
                                        null,
                                        List.of()))
                        .onTermination().invoke(() -> active.decrementAndGet());
            });
        }
    }
}
