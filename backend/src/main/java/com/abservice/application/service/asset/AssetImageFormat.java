package com.abservice.application.service.asset;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * アセットとして受け入れる画像形式
 *
 * <p>
 * 申告された Content-Type は信用せず、保管済み実体の先頭バイト列（マジックバイト）で形式を判定する。判定に必要な先頭 バイト数は
 * {@link #REQUIRED_PREFIX_BYTES}。
 * </p>
 */
public enum AssetImageFormat {

    /** JPEG（SOIマーカー FF D8 FF で始まる） */
    JPEG("image/jpeg", "jpg") {
        @Override
        boolean matches(byte[] prefix) {
            return startsWith(prefix, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        }
    },

    /** PNG（8バイトのPNG署名で始まる） */
    PNG("image/png", "png") {
        @Override
        boolean matches(byte[] prefix) {
            return startsWith(
                    prefix,
                    new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', (byte) 0x1A, '\n'});
        }
    },

    /** WebP（RIFFコンテナの先頭4バイトが RIFF、8バイト目から WEBP） */
    WEBP("image/webp", "webp") {
        @Override
        boolean matches(byte[] prefix) {
            return startsWith(prefix, "RIFF".getBytes(StandardCharsets.US_ASCII))
                    ? hasWebpChunk(prefix)
                    : false;
        }
    };

    /** 形式判定に必要な先頭バイト数（WebP の "WEBP" が12バイト目まで必要） */
    public static final int REQUIRED_PREFIX_BYTES = 12;

    /** RIFFコンテナ内で形式識別子（"WEBP"）が始まる位置 */
    private static final int WEBP_CHUNK_OFFSET = 8;

    private final String contentType;
    private final String extension;

    AssetImageFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    /**
     * @return この形式の Content-Type
     */
    public String contentType() {
        return contentType;
    }

    /**
     * @return この形式のファイル拡張子（先頭のドットを含まない）
     */
    public String extension() {
        return extension;
    }

    /**
     * 実体の先頭バイト列がこの形式かを判定します。
     *
     * @param prefix
     *            実体の先頭バイト列
     * @return この形式であれば true
     */
    abstract boolean matches(byte[] prefix);

    /**
     * Content-Type から受け入れ可能な形式を解決します。
     *
     * @param contentType
     *            クライアントが申告した Content-Type
     * @return 対応する形式。受け入れ対象外なら空
     */
    public static Optional<AssetImageFormat> ofContentType(String contentType) {
        return Stream.of(values())
                .filter(format -> format.contentType.equalsIgnoreCase(contentType))
                .findFirst();
    }

    /**
     * ファイル拡張子から受け入れ可能な形式を解決します。
     *
     * @param extension
     *            ファイル拡張子（先頭のドットを含まない）
     * @return 対応する形式。受け入れ対象外なら空
     */
    public static Optional<AssetImageFormat> ofExtension(String extension) {
        return Stream.of(values())
                .filter(format -> format.extension.equalsIgnoreCase(extension))
                .findFirst();
    }

    /**
     * 実体の先頭バイト列から形式を判定します。
     *
     * @param prefix
     *            実体の先頭バイト列
     * @return 一致した形式。いずれにも一致しなければ空
     */
    public static Optional<AssetImageFormat> ofContent(byte[] prefix) {
        return Stream.of(values())
                .filter(format -> format.matches(prefix))
                .findFirst();
    }

    /**
     * 受け入れ可能な Content-Type の一覧（エラーメッセージ用）。
     *
     * @return カンマ区切りの Content-Type
     */
    public static String acceptedContentTypes() {
        return Stream.of(values())
                .map(AssetImageFormat::contentType)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private static boolean startsWith(byte[] prefix, byte[] magic) {
        return prefix.length >= magic.length
                ? Arrays.equals(
                        Arrays.copyOf(prefix, magic.length),
                        magic)
                : false;
    }

    private static boolean hasWebpChunk(byte[] prefix) {
        return prefix.length >= REQUIRED_PREFIX_BYTES
                ? Arrays.equals(
                        Arrays.copyOfRange(
                                prefix,
                                WEBP_CHUNK_OFFSET,
                                REQUIRED_PREFIX_BYTES),
                        "WEBP".getBytes(StandardCharsets.US_ASCII))
                : false;
    }
}
