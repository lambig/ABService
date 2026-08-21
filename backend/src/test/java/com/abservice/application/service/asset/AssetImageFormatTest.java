package com.abservice.application.service.asset;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AssetImageFormat（受け入れ画像形式の判定）のテスト")
class AssetImageFormatTest {

    private static final byte[] JPEG_HEAD = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0,
            0};

    private static final byte[] PNG_HEAD = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', (byte) 0x1A, '\n', 0, 0, 0, 0};

    @Test
    @DisplayName("Content-Typeから形式を解決できる")
    void resolvesByContentType() {
        assertThat(AssetImageFormat.ofContentType("image/png")).contains(AssetImageFormat.PNG);
        assertThat(AssetImageFormat.ofContentType("IMAGE/JPEG")).contains(AssetImageFormat.JPEG);
        assertThat(AssetImageFormat.ofContentType("image/gif")).isEmpty();
    }

    @Test
    @DisplayName("拡張子から形式を解決できる")
    void resolvesByExtension() {
        assertThat(AssetImageFormat.ofExtension("jpg")).contains(AssetImageFormat.JPEG);
        assertThat(AssetImageFormat.ofExtension("WEBP")).contains(AssetImageFormat.WEBP);
        assertThat(AssetImageFormat.ofExtension("gif")).isEmpty();
    }

    @Test
    @DisplayName("マジックバイトでJPEGとPNGを判定できる")
    void detectsJpegAndPngByMagicBytes() {
        assertThat(AssetImageFormat.ofContent(JPEG_HEAD)).contains(AssetImageFormat.JPEG);
        assertThat(AssetImageFormat.ofContent(PNG_HEAD)).contains(AssetImageFormat.PNG);
    }

    @Test
    @DisplayName("RIFFコンテナは8バイト目からWEBPのときだけWebPと判定する")
    void detectsWebpOnlyWithWebpChunk() {
        assertThat(AssetImageFormat.ofContent(riffContainer("WEBP"))).contains(AssetImageFormat.WEBP);
        assertThat(AssetImageFormat.ofContent(riffContainer("WAVE"))).isEmpty();
    }

    @Test
    @DisplayName("先頭バイト列が短すぎる場合はいずれの形式とも判定しない")
    void rejectsTooShortPrefix() {
        assertThat(AssetImageFormat.ofContent(new byte[]{(byte) 0xFF})).isEmpty();
        assertThat(AssetImageFormat.ofContent(new byte[0])).isEmpty();
    }

    @Test
    @DisplayName("画像ではないバイト列は判定しない")
    void rejectsNonImageContent() {
        assertThat(AssetImageFormat.ofContent("plain text content".getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    @Test
    @DisplayName("受け入れ可能なContent-Typeを列挙できる")
    void listsAcceptedContentTypes() {
        assertThat(AssetImageFormat.acceptedContentTypes()).isEqualTo("image/jpeg, image/png, image/webp");
    }

    private static byte[] riffContainer(String chunk) {
        final var head = new byte[AssetImageFormat.REQUIRED_PREFIX_BYTES];
        System.arraycopy(
                "RIFF".getBytes(StandardCharsets.US_ASCII),
                0,
                head,
                0,
                4);
        System.arraycopy(
                chunk.getBytes(StandardCharsets.US_ASCII),
                0,
                head,
                8,
                4);
        return head;
    }
}
