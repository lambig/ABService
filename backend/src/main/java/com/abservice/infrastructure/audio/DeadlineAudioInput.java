package com.abservice.infrastructure.audio;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * 入力のopenとreadを有界の別実行枠で行い、消費側は全体期限で停止する。
 * 割込みを無視する入力はproducer枠を保持し続ける。snapshotへ書くのは消費側だけ。
 */
final class DeadlineAudioInput extends InputStream {
    private final ArrayBlockingQueue<Event> events = new ArrayBlockingQueue<>(1);
    private final AtomicReference<Event> current = new AtomicReference<>(new Data(ByteBuffer.allocate(0)));
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<Optional<Event>> terminal = new AtomicReference<>(Optional.empty());
    private final long started = System.nanoTime();
    private final long timeoutNanos;
    private final AtomicReference<Optional<Future<?>>> producer = new AtomicReference<>(Optional.empty());
    private final Callable<InputStream> source;
    private final ExecutorService executor;

    DeadlineAudioInput(
            Callable<InputStream> source,
            Duration timeout,
            ExecutorService executor) {
        timeoutNanos = Optional.of(timeout.toNanos())
                .filter(value -> value > 0)
                .orElseThrow(() -> new IllegalArgumentException("Invalid audio read deadline"));
        this.source = Objects.requireNonNull(source);
        this.executor = Objects.requireNonNull(executor);
    }

    @Override
    public int read() throws IOException {
        final byte[] value = new byte[1];
        return read(
                value,
                0,
                1) < 0
                        ? -1
                        : Byte.toUnsignedInt(value[0]);
    }

    @Override
    public synchronized int read(
            byte[] target,
            int offset,
            int length) throws IOException {
        Objects.checkFromIndexSize(
                offset,
                length,
                target.length);
        return length == 0
                ? 0
                : readNext(
                        target,
                        offset,
                        length);
    }

    private int readNext(
            byte[] target,
            int offset,
            int length) throws IOException {
        requireOpen();
        producer.updateAndGet(
                currentProducer -> currentProducer.isPresent()
                        ? currentProducer
                        : Optional.of(executor.submit(this::pump)));
        return switch (current.get()) {
            case Data data -> data.bytes().hasRemaining()
                    ? copy(
                            data.bytes(),
                            target,
                            offset,
                            length)
                    : receive(
                            target,
                            offset,
                            length);
            case Failed failed -> throw failed.failure();
            case Finished _ -> -1;
        };
    }

    private int receive(
            byte[] target,
            int offset,
            int length) throws IOException {
        try {
            final var ready = Optional.ofNullable(events.poll())
                    .or(this::finishedOrQueued);
            current.set(
                    ready.isPresent()
                            ? ready.orElseThrow()
                            : Optional.ofNullable(events.poll(remaining(), TimeUnit.NANOSECONDS))
                                    .orElseThrow(DeadlineAudioInput::timeout));
            return readNext(
                    target,
                    offset,
                    length);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Audio input interrupted");
        }
    }

    private Optional<Event> finishedOrQueued() {
        return terminal.get().map(this::queuedBeforeTerminal);
    }

    private Event queuedBeforeTerminal(Event event) {
        return Optional.ofNullable(events.poll())
                .orElse(event);
    }

    private static int copy(
            ByteBuffer data,
            byte[] target,
            int offset,
            int length) {
        final int count = Math.min(data.remaining(), length);
        data.get(
                target,
                offset,
                count);
        return count;
    }

    private long remaining() throws IOException {
        return Optional.of(timeoutNanos - (System.nanoTime() - started))
                .filter(value -> value > 0)
                .orElseThrow(DeadlineAudioInput::timeout);
    }

    private void requireOpen() throws IOException {
        Optional.of(closed.get())
                .filter(Boolean.FALSE::equals)
                .orElseThrow(() -> new IOException("Audio input closed"));
        remaining();
    }

    @Override
    public void close() {
        closed.set(true);
        producer.get().ifPresent(task -> task.cancel(true));
    }

    @SuppressWarnings("PMD.SingleUseLocalVariable") // RESOURCE-LIFETIME: 入力は暗黙のcloseまでproducerが所有する。
    private void pump() {
        try {
            requireOpen();
            try (var input = source.call()) {
                Stream.generate(() -> chunk(input))
                        .takeWhile(data -> data.bytes().hasRemaining())
                        .forEach(this::send);
            }
            finish(Finished.INSTANCE);
        } catch (Exception failure) {
            finish(new Failed(new IOException("Audio source failed", failure)));
        }
    }

    private void finish(Event event) {
        terminal.set(Optional.of(event));
        events.offer(event);
    }

    private Data chunk(InputStream input) {
        try {
            requireOpen();
            final byte[] bytes = new byte[8192];
            return new Data(ByteBuffer.wrap(
                    bytes,
                    0,
                    input.readNBytes(
                            bytes,
                            0,
                            bytes.length)));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private void send(Event event) {
        try {
            requireOpen();
            Optional.of(
                    events.offer(
                            event,
                            remaining(),
                            TimeUnit.NANOSECONDS))
                    .filter(Boolean::booleanValue)
                    .orElseThrow(DeadlineAudioInput::timeout);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(new InterruptedIOException("Audio input interrupted"));
        }
    }

    private static InterruptedIOException timeout() {
        return new InterruptedIOException("Audio input deadline exceeded");
    }

    private sealed interface Event permits Data, Failed, Finished {
    }

    private record Data(ByteBuffer bytes) implements Event {
        private Data {
            Objects.requireNonNull(bytes);
        }
    }

    private record Failed(IOException failure) implements Event {
        private Failed {
            Objects.requireNonNull(failure);
        }
    }

    private enum Finished implements Event {
        INSTANCE
    }
}
