package com.abservice.infrastructure.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.application.port.FlacInspectionLimits;
import com.abservice.application.port.InvalidFlacException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 実flacコマンドによる合成音源の検査。DB・S3・実作品を使用しない。 */
@DisplayName("非公開FLACの実デコード・資源上限・実体所有権")
class NativeFlacInspectorIntegrationTest {
    @TempDir
    private Path directory;

    @Test
    @DisplayName("16bit stereoの実測値を返し、元入力の変更から検査済み実体を分離する")
    void retainsVerifiedSnapshot() throws Exception {
        final byte[] bytes = fixture(2, 16);
        final byte[] expected = bytes.clone();
        final Path snapshots = Files.createDirectory(directory.resolve("snapshots"));
        final var inspector = new NativeFlacInspector("flac", snapshots);
        try (var inspected = inspector.inspect(new ByteArrayInputStream(bytes), FlacInspectionLimits.defaults())) {
            assertThat(inspected.metadata().byteLength()).isEqualTo(bytes.length);
            assertThat(inspected.metadata().sha256())
                    .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            assertThat(inspected.metadata().sampleRate()).isEqualTo(44100);
            assertThat(inspected.metadata().channels()).isEqualTo(2);
            assertThat(inspected.metadata().bitsPerSample()).isEqualTo(16);
            assertThat(inspected.metadata().totalSamples()).isEqualTo(4096);
            assertThat(inspected.metadata().durationMillis()).isEqualTo(93);
            Arrays.fill(bytes, (byte) 0);
            try (var verified = inspected.openStream()) {
                assertThat(verified.readAllBytes()).isEqualTo(expected);
            }
        }
        assertEmpty(snapshots);
    }

    @Test
    @DisplayName("24bit monoとサイズ上限ちょうどを受け入れる")
    void acceptsMonoAndExactByteLimit() throws Exception {
        final byte[] bytes = fixture(1, 24);
        final var limits = new FlacInspectionLimits(
                bytes.length,
                1,
                Duration.ofSeconds(10));
        try (var inspected = inspector().inspect(new ByteArrayInputStream(bytes), limits)) {
            assertThat(inspected.metadata().channels()).isEqualTo(1);
            assertThat(inspected.metadata().bitsPerSample()).isEqualTo(24);
        }
    }

    @Test
    @DisplayName("上限超過は保存中に打ち切り、失敗した実体を残さない")
    void stopsOversizedStream() throws Exception {
        final Path snapshots = Files.createDirectory(directory.resolve("snapshots"));
        final var inspector = new NativeFlacInspector("must-not-run", snapshots);
        final var limits = new FlacInspectionLimits(
                16,
                1,
                Duration.ofSeconds(10));
        assertThatThrownBy(() -> inspector.inspect(new ByteArrayInputStream(new byte[100_000]), limits))
                .isInstanceOf(InvalidFlacException.class).hasMessage("FLAC_SIZE_EXCEEDED");
        assertEmpty(snapshots);
    }

    @Test
    @DisplayName("FLACの識別子だけ、偽装した形式、切詰めを受け入れない")
    void rejectsHeaderOnlyAndTruncation() throws Exception {
        final byte[] valid = fixture(2, 16);
        rejects(new byte[]{'f', 'L', 'a', 'C'});
        rejects(new byte[]{'O', 'g', 'g', 'S'});
        rejects(Arrays.copyOf(valid, 42));
        rejects(Arrays.copyOf(valid, valid.length - 10));
        rejects(Arrays.copyOf(valid, valid.length - 1));
    }

    @Test
    @DisplayName("実フレーム破損・MD5不一致・後続ゴミを拒否する")
    void rejectsCorruption() throws Exception {
        final byte[] frame = fixture(2, 16);
        frame[frame.length - 1] ^= 1;
        rejects(frame);
        final byte[] md5 = fixture(2, 16);
        md5[26] ^= 1;
        rejects(md5);
        final byte[] trailing = Arrays.copyOf(md5, md5.length + 50);
        md5[26] ^= 1;
        System.arraycopy(
                md5,
                0,
                trailing,
                0,
                md5.length);
        rejects(trailing);
    }

    @Test
    @DisplayName("MD5なし・サンプル数なし・時間上限超過・申告サンプル数不一致を拒否する")
    void rejectsUnverifiableOrMisleadingMetadata() throws Exception {
        final byte[] bytes = fixture(2, 16);
        final byte[] noMd5 = bytes.clone();
        Arrays.fill(
                noMd5,
                26,
                42,
                (byte) 0);
        rejects(noMd5);
        rejects(withSampleCount(bytes, 0));
        rejects(withSampleCount(bytes, 44100L * 7201));
        rejects(withSampleCount(bytes, 2048));
        rejects(withSampleCount(bytes, 8192));
    }

    @Test
    @DisplayName("受入範囲外のチャンネル数・量子化をデコード前に拒否する")
    void rejectsUnsupportedProfiles() throws Exception {
        rejects(fixture(3, 16));
        rejects(fixture(1, 8));
    }

    @Test
    @DisplayName("デコーダ不在は入力不正と区別し、検査失敗の実体を削除する")
    void rejectsUnavailableDecoder() throws Exception {
        final byte[] bytes = fixture(2, 16);
        final Path snapshots = Files.createDirectory(directory.resolve("snapshots"));
        final var inspector = new NativeFlacInspector(directory.resolve("missing-decoder").toString(), snapshots);
        assertThatThrownBy(() -> inspector.inspect(new ByteArrayInputStream(bytes), FlacInspectionLimits.defaults()))
                .isInstanceOf(IOException.class);
        assertEmpty(snapshots);
    }

    @Test
    @DisplayName("デコードの時間切れを停止し、一時実体と検査枠を解放する")
    void timesOutAndReleasesResources() throws Exception {
        final byte[] bytes = fixture(2, 16);
        final Path script = directory.resolve("decoder");
        Files.writeString(script, "#!/bin/sh\nexec sleep 30\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        final Path snapshots = Files.createDirectory(directory.resolve("snapshots"));
        final var inspector = new NativeFlacInspector(script.toString(), snapshots);
        final var limits = new FlacInspectionLimits(
                1_000_000,
                1,
                Duration.ofMillis(100));
        assertThatThrownBy(() -> inspector.inspect(new ByteArrayInputStream(bytes), limits))
                .isInstanceOf(IOException.class).hasMessage("Audio inspection timed out");
        assertEmpty(snapshots);
        assertThatThrownBy(() -> inspector.inspect(new ByteArrayInputStream(bytes), limits))
                .isInstanceOf(IOException.class).hasMessage("Audio inspection timed out");
        assertEmpty(snapshots);
    }

    @Test
    @DisplayName("取得中断はI/O障害として扱い、一時実体を削除する")
    void cleansUpFailedRead() throws Exception {
        final Path snapshots = Files.createDirectory(directory.resolve("snapshots"));
        final var inspector = new NativeFlacInspector("flac", snapshots);
        try (var broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("synthetic transfer failure");
            }
        }) {
            assertThatThrownBy(() -> inspector.inspect(broken, FlacInspectionLimits.defaults()))
                    .isInstanceOf(IOException.class).hasMessage("synthetic transfer failure");
        }
        assertEmpty(snapshots);
    }

    private NativeFlacInspector inspector() {
        return new NativeFlacInspector("flac", directory);
    }

    @Test
    @DisplayName("同時検査は待ち行列へ積まず拒否し、実行中の検査は正常終了する")
    void boundsConcurrentInspection() throws Exception {
        final byte[] bytes = fixture(2, 16);
        final var started = new CountDownLatch(1);
        final var resume = new CountDownLatch(1);
        final var inspector = inspector();
        try (var source = new InputStream() {
            private final ByteArrayInputStream delegate = new ByteArrayInputStream(bytes);

            @Override
            public int read() throws IOException {
                started.countDown();
                try {
                    assertThat(resume.await(10, TimeUnit.SECONDS)).isTrue();
                    return delegate.read();
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IOException(failure);
                }
            }
        }; var executor = Executors.newSingleThreadExecutor()) {
            final var running = executor.submit(() -> inspector.inspect(source, FlacInspectionLimits.defaults()));
            try {
                assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(
                        () -> inspector.inspect(
                                new ByteArrayInputStream(bytes),
                                FlacInspectionLimits.defaults()))
                        .isInstanceOf(IOException.class).hasMessage("Audio inspection busy");
            } finally {
                resume.countDown();
            }
            try (var inspected = running.get(10, TimeUnit.SECONDS)) {
                assertThat(inspected.metadata().byteLength()).isEqualTo(bytes.length);
            }
        }
    }

    private void rejects(byte[] bytes) {
        assertThatThrownBy(() -> {
            try (var inspected = inspector()
                    .inspect(new ByteArrayInputStream(bytes), FlacInspectionLimits.defaults())) {
                assertThat(inspected.metadata()).isNotNull();
            }
        }).isInstanceOf(InvalidFlacException.class);
    }

    private static void assertEmpty(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            assertThat(files).isEmpty();
        }
    }

    private static byte[] withSampleCount(byte[] original, long count) {
        final byte[] copy = original.clone();
        final var buffer = ByteBuffer.wrap(copy);
        buffer.putLong(18, (buffer.getLong(18) & 0xfffffff000000000L) | count);
        return copy;
    }

    private byte[] fixture(int channels, int bits) throws Exception {
        return fixture(
                channels,
                bits,
                4096);
    }

    @Test
    @DisplayName("複数フレームと端数の最終フレームを実測して受け入れる")
    void countsMultipleFrames() throws Exception {
        final byte[] bytes = fixture(
                2,
                24,
                99999);
        try (var inspected = inspector().inspect(new ByteArrayInputStream(bytes), FlacInspectionLimits.defaults())) {
            assertThat(inspected.metadata().totalSamples()).isEqualTo(99999);
        }
    }

    @Test
    @DisplayName("解析出力待ちにも時間上限を適用して停止する")
    void timesOutWhileReadingAnalysis() throws Exception {
        final byte[] bytes = fixture(2, 16);
        final Path script = directory.resolve("decoder-analysis-timeout");
        Files.writeString(
                script,
                "#!/bin/sh\ncase \"$1\" in --test) exec flac \"$@\";; *) exec sleep 30;; esac\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        final Path snapshots = Files.createDirectory(directory.resolve("snapshots"));
        final var inspector = new NativeFlacInspector(script.toString(), snapshots);
        final var limits = new FlacInspectionLimits(
                1_000_000,
                1,
                Duration.ofSeconds(1));
        assertThatThrownBy(() -> inspector.inspect(new ByteArrayInputStream(bytes), limits))
                .isInstanceOf(IOException.class).hasMessage("Audio inspection timed out");
        assertEmpty(snapshots);
    }

    private byte[] fixture(
            int channels,
            int bits,
            int samples) throws Exception {
        final byte[] raw = new byte[samples * channels * bits / 8];
        IntStream.range(0, raw.length).forEach(i -> raw[i] = (byte) (i * 13));
        final Path input = Files.createTempFile(
                directory,
                "tone-",
                ".raw");
        final Path output = input.resolveSibling(input.getFileName() + ".flac");
        Files.write(input, raw);
        final var encoder = new ProcessBuilder("flac", "--silent", "--force-raw-format", "--endian=little",
                "--sign=signed", "--channels=" + channels, "--bps=" + bits, "--sample-rate=44100",
                "--no-padding", "--no-seektable", "--output-name=" + output, input.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        try {
            assertThat(encoder.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(encoder.exitValue()).isZero();
            return Files.readAllBytes(output);
        } finally {
            encoder.destroyForcibly().onExit().join();
            Files.deleteIfExists(input);
            Files.deleteIfExists(output);
        }
    }
}
