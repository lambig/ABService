package com.abservice.infrastructure.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("音源入力の全体期限と有界producer")
class DeadlineAudioInputTest {
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), Thread.ofPlatform().daemon().factory());

    @AfterEach
    void shutdown() throws Exception {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("小さいバッファを経由して全内容を読み、元入力のclose後にEOFを返す")
    void streamsAndCloses() throws Exception {
        final byte[] bytes = new byte[100000];
        final var closed = new AtomicInteger();
        try (var input = new DeadlineAudioInput(() -> new ByteArrayInputStream(bytes) {
            @Override
            public void close() {
                closed.incrementAndGet();
            }
        }, Duration.ofSeconds(5), executor)) {
            assertThat(input.readAllBytes()).isEqualTo(bytes);
            assertThat(input.read()).isEqualTo(-1);
            assertThat(closed.get()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("最初のread前には入力を開かず、close後も取得しない")
    void opensLazily() throws Exception {
        final var opened = new AtomicInteger();
        try (var input = new DeadlineAudioInput(() -> {
            opened.incrementAndGet();
            return InputStream.nullInputStream();
        }, Duration.ofSeconds(1), executor)) {
            assertThat(input.read(new byte[0])).isZero();
            input.close();
            assertThatThrownBy(input::read).isInstanceOf(IOException.class);
            assertThat(opened.get()).isZero();
        }
    }

    @Test
    @DisplayName("openが割込みを無視しても読込期限で戻り、遅れて取得した入力を閉じる")
    void boundsUncooperativeOpen() throws Exception {
        final var release = new CountDownLatch(1);
        final var opened = new CompletableFuture<Boolean>();
        final var closed = new CompletableFuture<Boolean>();
        try (var input = new DeadlineAudioInput(() -> {
            opened.complete(true);
            awaitIgnoringInterrupt(release);
            return new ByteArrayInputStream(new byte[]{1}) {
                @Override
                public void close() {
                    closed.complete(true);
                }
            };
        }, Duration.ofSeconds(1), executor)) {
            try {
                assertThatThrownBy(input::read).isInstanceOf(InterruptedIOException.class);
                assertThat(opened.get(2, TimeUnit.SECONDS)).isTrue();
                input.close();
                assertThat(executor.getActiveCount()).isEqualTo(1);
            } finally {
                release.countDown();
            }
            assertThat(closed.get(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("readが割込みを無視しても期限切れ後にproducer枠を増やさない")
    void boundsUncooperativeRead() throws Exception {
        final var release = new CountDownLatch(1);
        final var entered = new CompletableFuture<Boolean>();
        try (var input = new DeadlineAudioInput(() -> new InputStream() {
            @Override
            public int read() {
                entered.complete(true);
                awaitIgnoringInterrupt(release);
                return -1;
            }
        }, Duration.ofSeconds(1), executor)) {
            try {
                assertThatThrownBy(input::read).isInstanceOf(InterruptedIOException.class);
                assertThat(entered.get(2, TimeUnit.SECONDS)).isTrue();
                input.close();
                assertThat(executor.getPoolSize()).isEqualTo(1);
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    @DisplayName("先読み後の読込障害をEOFへ変換しない")
    void propagatesReadFailure() throws Exception {
        try (var input = new DeadlineAudioInput(() -> new InputStream() {
            private final AtomicInteger reads = new AtomicInteger();

            @Override
            public int read() throws IOException {
                return reads.incrementAndGet() <= 8192
                        ? 1
                        : fail();
            }

            private int fail() throws IOException {
                throw new IOException("synthetic input failure");
            }
        }, Duration.ofSeconds(2), executor)) {
            assertThatThrownBy(input::readAllBytes).isInstanceOf(IOException.class).hasMessage("Audio source failed");
        }
    }

    @Test
    @DisplayName("入力closeの失敗を正常EOFとして返さない")
    void propagatesCloseFailure() throws Exception {
        try (var input = new DeadlineAudioInput(() -> new ByteArrayInputStream(new byte[]{1}) {
            @Override
            public void close() throws IOException {
                throw new IOException("synthetic close failure");
            }
        }, Duration.ofSeconds(2), executor)) {
            assertThatThrownBy(input::readAllBytes).isInstanceOf(IOException.class).hasMessage("Audio source failed");
        }
    }

    private static void awaitIgnoringInterrupt(CountDownLatch release) {
        try {
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException ignored) {
            awaitIgnoringInterrupt(release);
        }
    }

    @Test
    @DisplayName("終了通知と最終バッファが近接しても残りのバイトを取りこぼさない")
    void drainsBeforeTerminal() throws Exception {
        for (final int size : IntStream.range(0, 100).toArray()) {
            final byte[] bytes = new byte[8192 + size];
            try (var input = new DeadlineAudioInput(
                    () -> new ByteArrayInputStream(bytes),
                    Duration.ofSeconds(5),
                    executor)) {
                assertThat(input.readAllBytes()).hasSize(bytes.length);
            }
        }
    }
}
