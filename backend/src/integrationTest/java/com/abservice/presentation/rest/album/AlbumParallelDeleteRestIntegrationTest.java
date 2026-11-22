package com.abservice.presentation.rest.album;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Independent HTTP requests must not share the pending Hibernate delete
 * operation.
 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
class AlbumParallelDeleteRestIntegrationTest {

    @Test
    @DisplayName("別々のアルバムを同時に削除しても全件が完了し、削除した行が残らない")
    void deletesAcrossIndependentTransactionsComplete() throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int attempt = 0; attempt < 4; attempt++) {
                deleteBatch(executor, attempt);
            }
        }
    }

    private static void deleteBatch(ExecutorService executor, int attempt) throws Exception {
        final var albumIds = IntStream.range(0, 8)
                .mapToObj(AlbumParallelDeleteRestIntegrationTest::createAlbum)
                .toList();
        final var ready = new CountDownLatch(albumIds.size());
        final var start = new CountDownLatch(1);
        final var results = albumIds.stream().map(id -> executor.submit(() -> {
            ready.countDown();
            assertThat(start.await(10, TimeUnit.SECONDS)).as("Concurrent delete start").isTrue();
            return authorized()
                    .config(
                            RestAssuredConfig.config().httpClient(
                                    HttpClientConfig.httpClientConfig()
                                            .setParam("http.connection.timeout", 10000)
                                            .setParam("http.socket.timeout", 10000)))
                    .when().delete("/api/v1/albums/" + id).statusCode();
        })).toList();
        try {
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            start.countDown();
        }
        for (final var result : results) {
            assertThat(result.get(15, TimeUnit.SECONDS))
                    .as("Independent deletion must complete successfully, attempt=%d", attempt)
                    .isEqualTo(200);
        }
        for (final var id : albumIds) {
            authorized().when().get("/api/v1/admin/albums/" + id).then().statusCode(404);
        }
    }

    private static String createAlbum(int index) {
        return authorized().contentType(ContentType.JSON)
                .body("""
                        {"title":"Concurrent delete %d","releaseDate":"2026-01-01",
                         "artistDisplayName":"Synthetic artist"}
                        """.formatted(index))
                .when().post("/api/v1/albums").then().statusCode(201).extract().path("albumId");
    }
}
