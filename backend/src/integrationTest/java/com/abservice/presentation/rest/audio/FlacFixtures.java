package com.abservice.presentation.rest.audio;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 実作品を使わず、乱数PCMから正常なFLACを合成する。検査器と同じ {@code flac} CLI を使う。
 *
 * <p>
 * PCM は 44.1kHz・ステレオ・16bit の符号付きリトルエンディアンで、1秒あたり 176,400 バイトになる。
 * </p>
 */
final class FlacFixtures {
    /** 44.1kHz・ステレオ・16bit の1秒分の PCM バイト数。 */
    static final int ONE_SECOND_OF_PCM = 44_100 * 2 * 2;

    private FlacFixtures() {
    }

    /**
     * @param directory
     *            一時ファイルを置く場所（試験が所有する一時ディレクトリ）
     * @param pcmBytes
     *            元の PCM のバイト数
     * @param seed
     *            乱数の種。種が違えば別の実体になる
     * @return 合成した FLAC ファイル
     */
    static Path encodedFile(
            Path directory,
            int pcmBytes,
            long seed) throws Exception {
        final var input = directory.resolve(UUID.randomUUID() + ".raw");
        final var output = input.resolveSibling(input.getFileName() + ".flac");
        final var random = new Random(seed);
        final byte[] block = new byte[4096];
        try (var raw = Files.newOutputStream(input)) {
            for (int written = 0; written < pcmBytes; written += block.length) {
                random.nextBytes(block);
                raw.write(
                        block,
                        0,
                        Math.min(block.length, pcmBytes - written));
            }
        }
        final var encoder = new ProcessBuilder("flac", "--silent", "--force-raw-format", "--endian=little",
                "--sign=signed",
                "--channels=2", "--bps=16", "--sample-rate=44100", "--no-padding", "--no-seektable",
                "--output-name=" + output, input.toString()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        try {
            assertThat(encoder.waitFor(30, TimeUnit.SECONDS)).isTrue();
            assertThat(encoder.exitValue()).isZero();
            return output;
        } finally {
            encoder.destroyForcibly().onExit().join();
        }
    }
}
