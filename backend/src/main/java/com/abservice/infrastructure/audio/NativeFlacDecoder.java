package com.abservice.infrastructure.audio;

import com.abservice.application.port.FlacMetadata;
import com.abservice.application.port.InvalidFlacException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * testモードに加え、解析された実フレームの範囲とサンプル数を照合する。
 * flac単体は末尾データやSTREAMINFOのサンプル数相違を常に拒否するわけではない。
 * 解析出力は逐次集計し保存しない。2回の起動全体で同じ時間予算を共有する。
 */
final class NativeFlacDecoder {
    private static final Pattern FRAME = Pattern.compile(
            "frame=(\\d+)\\toffset=(\\d+)\\tbits=(\\d+)\\tblocksize=(\\d+)"
                    + "\\tsample_rate=(\\d+)\\tchannels=(\\d+)\\tchannel_assignment=[A-Z_]+");

    private NativeFlacDecoder() {
    }

    static void validate(
            String executable,
            Path snapshot,
            FlacMetadata metadata,
            Duration timeout) throws IOException {
        final long started = System.nanoTime();
        try {
            test(
                    executable,
                    snapshot,
                    started,
                    timeout);
            final var frames = analyze(
                    executable,
                    snapshot,
                    metadata,
                    started,
                    timeout);
            FlacStreamInfo.require(frames.count() > 0, "FLAC_FRAMES_REQUIRED");
            FlacStreamInfo.require(frames.end() == metadata.byteLength(), "FLAC_TRAILING_DATA");
            FlacStreamInfo.require(frames.samples() == metadata.totalSamples(), "FLAC_SAMPLE_COUNT_MISMATCH");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("Audio inspection interrupted", failure);
        }
    }

    private static ProcessBuilder command(
            String executable,
            String mode,
            Path snapshot) {
        return new ProcessBuilder(executable, mode, "--silent", "--warnings-as-errors", "--stdout",
                "--", snapshot.toAbsolutePath().toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD);
    }

    private static void test(
            String executable,
            Path snapshot,
            long started,
            Duration timeout)
            throws IOException, InterruptedException {
        final var process = command(
                executable,
                "--test",
                snapshot)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        try {
            finish(
                    process,
                    started,
                    timeout);
        } finally {
            process.destroyForcibly().onExit().join();
        }
    }

    private static FrameTotals analyze(String executable, Path snapshot, FlacMetadata metadata,
            long started, Duration timeout) throws IOException, InterruptedException {
        try (@SuppressWarnings("PMD.SingleUseLocalVariable") // RESOURCE-CLOSE: owns implicit close.
        var reader = Executors.newSingleThreadExecutor()) {
            final var process = command(
                    executable,
                    "--analyze",
                    snapshot).start();
            try {
                return finishWithFrames(
                        process,
                        awaitFrames(reader.submit(() -> readFrames(process, metadata)), remaining(started, timeout)),
                        started,
                        timeout);
            } finally {
                process.destroyForcibly().onExit().join();
            }
        }
    }

    private static FrameTotals awaitFrames(Future<FrameTotals> result, long millis)
            throws IOException, InterruptedException {
        try {
            return result.get(millis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException failure) {
            throw new IOException("Audio inspection timed out", failure);
        } catch (ExecutionException failure) {
            throw Optional.ofNullable(failure.getCause())
                    .filter(InvalidFlacException.class::isInstance).map(InvalidFlacException.class::cast)
                    .orElseThrow(() -> new IOException("Audio analysis failed", failure));
        }
    }

    private static FrameTotals readFrames(Process process, FlacMetadata metadata) throws IOException {
        try (@SuppressWarnings("PMD.SingleUseLocalVariable") // RESOURCE-CLOSE: owns implicit close.
        var lines = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.US_ASCII))) {
            return lines.lines().filter(line -> line.startsWith("frame="))
                    .map(line -> frame(line, metadata))
                    .reduce(
                            new FrameTotals(
                                    0,
                                    0,
                                    0),
                            FrameTotals::append);
        }
    }

    private static FrameTotals frame(String line, FlacMetadata metadata) {
        final var fields = FRAME.matcher(line);
        FlacStreamInfo.require(fields.matches(), "FLAC_ANALYSIS_FORMAT_UNSUPPORTED");
        final long offset = Long.parseLong(fields.group(2));
        final long bits = Long.parseLong(fields.group(3));
        final long samples = Long.parseLong(fields.group(4));
        FlacStreamInfo.require(bits > 0, "FLAC_FRAME_SIZE_INVALID");
        FlacStreamInfo.require(bits % 8 == 0, "FLAC_FRAME_SIZE_INVALID");
        FlacStreamInfo.require(samples > 0, "FLAC_FRAME_SAMPLES_INVALID");
        FlacStreamInfo.require(Long.parseLong(fields.group(5)) == metadata.sampleRate(), "FLAC_FRAME_RATE_MISMATCH");
        FlacStreamInfo.require(Long.parseLong(fields.group(6)) == metadata.channels(), "FLAC_FRAME_CHANNELS_MISMATCH");
        return new FrameTotals(
                Long.parseLong(fields.group(1)) + 1,
                offset + bits / 8,
                samples,
                offset);
    }

    private static long remaining(long started, Duration timeout) throws IOException {
        return Optional.of(timeout.toMillis() - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                .filter(value -> value > 0).orElseThrow(() -> new IOException("Audio inspection timed out"));
    }

    private static FrameTotals finishWithFrames(
            Process process,
            FrameTotals frames,
            long started,
            Duration timeout) throws IOException, InterruptedException {
        finish(
                process,
                started,
                timeout);
        return frames;
    }

    private static void finish(
            Process process,
            long started,
            Duration timeout)
            throws IOException, InterruptedException {
        Optional.of(process.waitFor(remaining(started, timeout), TimeUnit.MILLISECONDS))
                .filter(Boolean::booleanValue).orElseThrow(() -> new IOException("Audio inspection timed out"));
        FlacStreamInfo.require(process.exitValue() == 0, "FLAC_DECODE_FAILED");
    }

    private record FrameTotals(long count, long end, long samples, long start) {
        private FrameTotals(
                long count,
                long end,
                long samples) {
            this(
                    count,
                    end,
                    samples,
                    0);
        }

        private FrameTotals append(FrameTotals next) {
            FlacStreamInfo.require(next.count() == count + 1, "FLAC_FRAME_SEQUENCE_INVALID");
            FlacStreamInfo.require(
                    count == 0
                            ? next.start() >= 42
                            : next.start() == end,
                    "FLAC_FRAME_GAP");
            return new FrameTotals(
                    next.count(),
                    next.end(),
                    samples + next.samples());
        }
    }
}
