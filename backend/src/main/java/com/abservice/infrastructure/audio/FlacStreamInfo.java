package com.abservice.infrastructure.audio;

import com.abservice.application.port.FlacInspectionLimits;
import com.abservice.application.port.FlacMetadata;
import com.abservice.application.port.InvalidFlacException;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/** STREAMINFOの受入条件。実フレーム・CRC・PCMのMD5検証はデコーダが担当する。 */
final class FlacStreamInfo {
    private FlacStreamInfo() {
    }

    static FlacMetadata read(
            Path file,
            long bytes,
            String sha256,
            FlacInspectionLimits limits) throws IOException {
        try (var input = new DataInputStream(Files.newInputStream(file))) {
            require(input.readInt() == 0x664c6143, "FLAC_SIGNATURE_REQUIRED");
            require((input.readUnsignedByte() & 0x7f) == 0, "FLAC_STREAMINFO_REQUIRED");
            require(input.readUnsignedByte() == 0, "FLAC_STREAMINFO_INVALID");
            require(input.readUnsignedShort() == 34, "FLAC_STREAMINFO_INVALID");
            input.skipNBytes(10);
            final long packed = input.readLong();
            final int rate = (int) (packed >>> 44);
            final int channels = (int) ((packed >>> 41) & 7) + 1;
            final int bits = (int) ((packed >>> 36) & 31) + 1;
            final long samples = packed & 0xfffffffffL;
            final byte[] md5 = input.readNBytes(16);
            require(md5.length == 16, "FLAC_STREAMINFO_INVALID");
            require(
                    IntStream.range(0, md5.length)
                            .anyMatch(i -> md5[i] != 0),
                    "FLAC_MD5_REQUIRED");
            require(rate >= 8000, "FLAC_SAMPLE_RATE_UNSUPPORTED");
            require(rate <= 96000, "FLAC_SAMPLE_RATE_UNSUPPORTED");
            require(channels <= 2, "FLAC_CHANNELS_UNSUPPORTED");
            require(
                    Set.of(16, 24)
                            .contains(bits),
                    "FLAC_BIT_DEPTH_UNSUPPORTED");
            require(samples > 0, "FLAC_SAMPLE_COUNT_REQUIRED");
            require((double) samples / rate <= limits.maxDurationSeconds(), "FLAC_DURATION_EXCEEDED");
            return new FlacMetadata(
                    bytes,
                    sha256,
                    rate,
                    channels,
                    bits,
                    samples);
        } catch (EOFException failure) {
            throw new InvalidFlacException("FLAC_TRUNCATED_HEADER");
        }
    }

    static void require(boolean valid, String code) {
        Optional.of(valid)
                .filter(Boolean::booleanValue)
                .orElseThrow(() -> new InvalidFlacException(code));
    }
}
