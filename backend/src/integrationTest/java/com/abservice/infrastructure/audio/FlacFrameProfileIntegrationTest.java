package com.abservice.infrastructure.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.application.port.FlacInspectionLimits;
import com.abservice.application.port.InvalidFlacException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.stream.IntStream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 無音のconstant subframeを生成し、CRC/MD5が正しい量子化変更を実デコーダで検査する。 */
class FlacFrameProfileIntegrationTest {
    @TempDir
    private Path directory;
    private AudioTemporaryStore temporaryStore;

    @BeforeEach
    void openTemporaryStore() throws Exception {
        temporaryStore = AudioTemporaryStore.open(directory.resolve("snapshots"), 512L * 1024 * 1024);
    }

    @AfterEach
    void closeTemporaryStore() throws Exception {
        temporaryStore.close();
    }

    @ParameterizedTest
    @CsvSource({"16, 8, 24", "16, 24, 16", "24, 16, 24"})
    void rejectsChangingDepthThroughRealDecoder(
            int streamBits,
            int middleBits,
            int lastBits) throws Exception {
        final byte[] bytes = fixture(streamBits, new int[]{streamBits, middleBits, lastBits});
        final Path snapshots = directory.resolve("snapshots");
        final var inspector = new NativeFlacInspector("flac", temporaryStore);
        assertThatThrownBy(() -> {
            try (var inspected = inspector.inspect(new ByteArrayInputStream(bytes), FlacInspectionLimits.defaults())) {
                assertThat(inspected.metadata()).isNotNull();
            }
        }).isInstanceOf(InvalidFlacException.class);
        try (var files = Files.list(snapshots)) {
            assertThat(files.map(path -> path.getFileName().toString())).containsExactly(".audio-owner.lock");
        }
    }

    @ParameterizedTest
    @CsvSource({"16, 16", "24, 24", "16, 0", "24, 0"})
    void acceptsConsistentDepthAndStreamInfoInheritance(int streamBits, int frameBits) throws Exception {
        final byte[] bytes = fixture(streamBits, new int[]{frameBits, frameBits, frameBits});
        final var inspector = new NativeFlacInspector("flac", temporaryStore);
        try (var inspected = inspector.inspect(new ByteArrayInputStream(bytes), FlacInspectionLimits.defaults())) {
            assertThat(inspected.metadata().bitsPerSample()).isEqualTo(streamBits);
            assertThat(inspected.metadata().totalSamples()).isEqualTo(48);
        }
    }

    /** CLI自体がbit depth変更を拒否する版でも、デコード成功後の独立した受入検査を通す。 */
    @ParameterizedTest
    @CsvSource({"16, 8, 24", "16, 24, 16", "24, 16, 24"})
    void rejectsChangingDepthEvenWhenDecoderAccepts(
            int streamBits,
            int middleBits,
            int lastBits) throws Exception {
        final int[] depths = {streamBits, middleBits, lastBits};
        final byte[] bytes = fixture(streamBits, depths);
        final Path decoder = directory.resolve("accepting-decoder");
        Files.writeString(
                decoder,
                "#!/bin/sh\ncase \"$1\" in --test) exit 0;; --analyze) cat <<'FRAMES'\n"
                        + analysis(streamBits, depths) + "FRAMES\n;; *) exit 1;; esac\n");
        Files.setPosixFilePermissions(decoder, PosixFilePermissions.fromString("rwx------"));
        final Path snapshots = directory.resolve("snapshots");
        final var inspector = new NativeFlacInspector(decoder.toString(), temporaryStore);
        assertThatThrownBy(() -> {
            try (var inspected = inspector.inspect(new ByteArrayInputStream(bytes), FlacInspectionLimits.defaults())) {
                assertThat(inspected.metadata()).isNotNull();
            }
        }).isInstanceOf(InvalidFlacException.class).hasMessage("FLAC_FRAME_BIT_DEPTH_MISMATCH");
        try (var files = Files.list(snapshots)) {
            assertThat(files.map(path -> path.getFileName().toString())).containsExactly(".audio-owner.lock");
        }
    }

    private static String analysis(int streamBits, int[] depths) {
        final var output = new StringBuilder();
        IntStream.range(0, depths.length).forEach(index -> {
            final int offset = 42 + IntStream.range(0, index).map(
                    previous -> frame(
                            previous,
                            depths[previous],
                            streamBits).length)
                    .sum();
            output.append("frame=").append(index).append("\toffset=").append(offset).append("\tbits=")
                    .append(
                            frame(
                                    index,
                                    depths[index],
                                    streamBits).length * 8)
                    .append("\tblocksize=16\tsample_rate=44100\tchannels=1\tchannel_assignment=INDEPENDENT\n");
        });
        return output.toString();
    }

    /** RFC 9639: 16サンプルずつのmono・固定block、CRCとPCM MD5を含む。 */
    private static byte[] fixture(int streamBits, int[] depths) throws Exception {
        final var output = new ByteArrayOutputStream();
        final var info = ByteBuffer.allocate(42);
        info.putInt(0x664c6143).putInt(0x80000022);
        info.putShort((short) 16).putShort((short) 16).put(new byte[6]);
        info.putLong((44100L << 44) | ((long) (streamBits - 1) << 36) | (depths.length * 16L));
        final int pcmBytes = IntStream.of(depths).map(bits -> effectiveBits(bits, streamBits) * 2).sum();
        info.put(MessageDigest.getInstance("MD5").digest(new byte[pcmBytes]));
        output.writeBytes(info.array());
        IntStream.range(0, depths.length).forEach(
                index -> output.writeBytes(
                        frame(
                                index,
                                depths[index],
                                streamBits)));
        return output.toByteArray();
    }

    private static byte[] frame(
            int number,
            int bits,
            int streamBits) {
        final var output = new ByteArrayOutputStream();
        output.writeBytes(new byte[]{(byte) 0xff, (byte) 0xf8, 0x69, (byte) (depthCode(bits) << 1), (byte) number, 15});
        output.write(crc(output.toByteArray(), 8));
        output.write(0);
        output.writeBytes(new byte[effectiveBits(bits, streamBits) / 8]);
        final int checksum = crc(output.toByteArray(), 16);
        output.write(checksum >>> 8);
        output.write(checksum);
        return output.toByteArray();
    }

    private static int effectiveBits(int bits, int streamBits) {
        return bits == 0
                ? streamBits
                : bits;
    }

    private static int depthCode(int bits) {
        return switch (bits) {
            case 0 -> 0;
            case 8 -> 1;
            case 16 -> 4;
            case 24 -> 6;
            default -> throw new IllegalArgumentException("Unsupported fixture depth");
        };
    }

    private static int crc(byte[] bytes, int width) {
        return IntStream.range(0, bytes.length).reduce(
                0,
                (sum, index) -> IntStream.iterate(
                        sum ^ ((bytes[index] & 0xff) << (width - 8)),
                        value -> ((value << 1) ^ ((value & (1 << (width - 1))) == 0
                                ? 0
                                : width == 8
                                        ? 0x07
                                        : 0x8005))
                                & ((1 << width) - 1))
                        .skip(8).findFirst().orElseThrow());
    }
}
