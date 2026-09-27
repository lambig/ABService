package com.abservice.infrastructure.audio;

import com.abservice.application.port.FlacInspectionLimits;
import com.abservice.application.port.FlacInspector;
import com.abservice.application.port.FlacMetadata;
import com.abservice.application.port.InspectedAudio;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 * libFLACのCLIで全体を検査する。メモリ上に音源全体や展開済みPCMを保持しない。
 * 引数はシェルを通さず、外部ファイル名・タグ・資格情報はログや例外へ載せない。
 * 同一インスタンスは1検査だけを受け入れる。成功時の一時実体は呼び出し側がcloseする。
 */
public final class NativeFlacInspector implements FlacInspector {
    private final String executable;
    private final Path temporaryDirectory;
    private final Semaphore admission = new Semaphore(1);

    public NativeFlacInspector(String executable, Path temporaryDirectory) {
        this.executable = Objects.requireNonNull(executable);
        this.temporaryDirectory = Objects.requireNonNull(temporaryDirectory);
    }

    @Override
    public InspectedAudio inspect(InputStream source, FlacInspectionLimits limits) throws IOException {
        requireAvailable(admission.tryAcquire(), "Audio inspection busy");
        try {
            return inspectOwnedSnapshot(source, limits);
        } finally {
            admission.release();
        }
    }

    private InspectedAudio inspectOwnedSnapshot(InputStream source, FlacInspectionLimits limits) throws IOException {
        final Path snapshot = Files.createTempFile(
                temporaryDirectory,
                "flac-",
                ".flac");
        try {
            final var metadata = copyAndRead(
                    source,
                    snapshot,
                    limits);
            NativeFlacDecoder.validate(
                    executable,
                    snapshot,
                    metadata,
                    limits.decodeTimeout());
            return new Snapshot(snapshot, metadata);
        } catch (IOException | RuntimeException failure) {
            discardAfterFailure(snapshot, failure);
            throw failure;
        }
    }

    private static FlacMetadata copyAndRead(
            InputStream source,
            Path snapshot,
            FlacInspectionLimits limits)
            throws IOException {
        final var digest = sha256();
        final var total = new AtomicLong();
        final byte[] buffer = new byte[8192];
        try (var output = Files.newOutputStream(snapshot)) {
            IntStream.generate(() -> readChunk(source, buffer))
                    .takeWhile(count -> count > 0)
                    .forEach(
                            count -> copyChunk(
                                    output,
                                    buffer,
                                    count,
                                    digest,
                                    total,
                                    limits.maxBytes()));
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
        return FlacStreamInfo.read(
                snapshot,
                total.get(),
                HexFormat.of().formatHex(digest.digest()),
                limits);
    }

    private static int readChunk(InputStream source, byte[] buffer) {
        try {
            return source.readNBytes(
                    buffer,
                    0,
                    buffer.length);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void copyChunk(OutputStream output, byte[] buffer, int count, MessageDigest digest,
            AtomicLong total, long maxBytes) {
        FlacStreamInfo.require(total.addAndGet(count) <= maxBytes, "FLAC_SIZE_EXCEEDED");
        try {
            output.write(
                    buffer,
                    0,
                    count);
            digest.update(
                    buffer,
                    0,
                    count);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private static void requireAvailable(boolean available, String message) throws IOException {
        Optional.of(available)
                .filter(Boolean::booleanValue)
                .orElseThrow(() -> new IOException(message));
    }

    private static void discardAfterFailure(Path file, Exception failure) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    /** パスを公開せず、読み取りだけを提供する検査済み実体。 */
    private record Snapshot(Path file, FlacMetadata metadata) implements InspectedAudio {
        @Override
        public InputStream openStream() throws IOException {
            return Files.newInputStream(file);
        }

        @Override
        public void close() throws IOException {
            Files.deleteIfExists(file);
        }
    }
}
