package com.abservice.application.audio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 試聴端末のBearerトークン。発行応答で一度だけ渡し、サーバーにはdigestだけを残す。
 *
 * <p>
 * 管理セッション（{@code abs_session_}）と接頭辞で区別し、認証機構が照合先を選べるようにする。 256
 * bitの暗号学的乱数をhexで表し、digestはSHA-256のhexとする。
 * </p>
 *
 * @param value
 *            接頭辞付きのトークン本体
 */
public record ListeningDeviceToken(String value) {
    private static final String PREFIX = "abs_device_";
    private static final int RANDOM_HEX_LENGTH = 64;
    private static final SecureRandom RANDOM = new SecureRandom();

    public ListeningDeviceToken {
        Optional.of(value)
                .filter(ListeningDeviceToken::isWellFormed)
                .orElseThrow(() -> new IllegalArgumentException("Malformed listening device token"));
    }

    public static ListeningDeviceToken issue() {
        final byte[] bytes = new byte[RANDOM_HEX_LENGTH / 2];
        RANDOM.nextBytes(bytes);
        return new ListeningDeviceToken(PREFIX + HexFormat.of().formatHex(bytes));
    }

    /** 接頭辞と長さだけの形式判定。照合はdigestで行う。 */
    public static boolean isWellFormed(String presented) {
        return presented.startsWith(PREFIX)
                ? presented.length() == PREFIX.length() + RANDOM_HEX_LENGTH
                : false;
    }

    public static String digestOf(String presented) {
        return HexFormat.of().formatHex(sha256().digest(presented.getBytes(StandardCharsets.UTF_8)));
    }

    public String digest() {
        return digestOf(value);
    }

    @Override
    public String toString() {
        return "ListeningDeviceToken[<redacted>]";
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }
}
