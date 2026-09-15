package com.abservice.presentation.rest.security;

import static java.util.function.Predicate.not;

import com.abservice.presentation.rest.security.response.AdminSessionResponse;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 単一backendプロセスの管理セッション。認証情報は業務モデルへ持ち込まない。
 *
 * <p>
 * 256 bitの不透明トークンを発行し、サーバ側にはdigestと固定の失効時刻だけを置く。期限は30分で、
 * 利用によって延長しない。最大128件とし、満杯なら最も古いセッションを失効させる。状態は不変Mapの
 * 原子的な置換で更新し、発行と破棄が競合しても消したセッションを復活させない。
 * </p>
 *
 * <p>
 * 再起動・再配布で全セッションが失効する。APIキーの更新は設定を再読込するbackend再配布と組にする。
 * 複数backendへ拡張するときは共有セッションストアへ置き換える（DECISIONS 22）。
 * </p>
 */
@ApplicationScoped
public class AdminSessions {

    static final String API_KEY_IDENTITY = "abservice.admin.api-key-identity";
    static final String SESSION_DIGEST = "abservice.admin.session-digest";
    static final Duration LIFETIME = Duration.ofMinutes(30);
    static final int MAX_SESSIONS = 128;

    private static final String TOKEN_PREFIX = "abs_session_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Clock clock;
    private final AtomicReference<Map<String, Instant>> sessions = new AtomicReference<>(Map.of());

    /** 本番の時計。テストは別の時計を渡して境界を検証する。 */
    public AdminSessions() {
        this(Clock.systemUTC());
    }

    AdminSessions(Clock clock) {
        this.clock = clock;
    }

    /**
     * 元のAPIキーで認証した要求だけが交換できる。セッショントークンで期限を延長できない。
     *
     * @param identity
     *            検証済みの要求元
     * @return 発行したトークンと固定の期限
     */
    public AdminSessionResponse exchange(SecurityIdentity identity) {
        Optional.of(identity)
                .filter(AdminSessions::isApiKeyIdentity)
                .orElseThrow(ForbiddenException::new);
        return issue();
    }

    private static boolean isApiKeyIdentity(SecurityIdentity identity) {
        return Boolean.TRUE.equals(identity.getAttribute(API_KEY_IDENTITY));
    }

    private AdminSessionResponse issue() {
        final var issued = new AdminSessionResponse(randomToken(), clock.instant().plus(LIFETIME));
        sessions.updateAndGet(existing -> including(existing, issued));
        return issued;
    }

    private Map<String, Instant> including(Map<String, Instant> existing, AdminSessionResponse issued) {
        return Stream.concat(
                existing.entrySet().stream()
                        .filter(entry -> entry.getValue().isAfter(clock.instant()))
                        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                        .limit(MAX_SESSIONS - 1L),
                Stream.of(Map.entry(digest(issued.token()), issued.expiresAt())))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * 有効なトークンのdigestだけを返す。期限の境界時刻は既に無効。
     *
     * @param token
     *            提示されたBearer
     * @return 有効ならセッションの識別子
     */
    public Optional<String> authenticatedDigest(String token) {
        return Optional.of(token)
                .filter(presented -> presented.startsWith(TOKEN_PREFIX))
                .filter(presented -> presented.length() == TOKEN_PREFIX.length() + 64)
                .map(AdminSessions::digest)
                .filter(this::isActive);
    }

    private boolean isActive(String digest) {
        return Optional.ofNullable(sessions.get().get(digest))
                .filter(expiration -> expiration.isAfter(clock.instant()))
                .isPresent();
    }

    /**
     * 呼出元のセッションだけを失効させる。元のAPIキーではセッションを選べない。
     *
     * @param identity
     *            セッションで認証した要求元
     */
    public void revoke(SecurityIdentity identity) {
        revokeDigest(Optional.ofNullable(identity.<String>getAttribute(SESSION_DIGEST))
                .orElseThrow(ForbiddenException::new));
    }

    private void revokeDigest(String digest) {
        sessions.updateAndGet(existing -> existing.entrySet().stream()
                .filter(not(entry -> entry.getKey().equals(digest)))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    private static String randomToken() {
        final byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return TOKEN_PREFIX + HexFormat.of().formatHex(bytes);
    }

    private static String digest(String token) {
        return HexFormat.of().formatHex(sha256().digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }
}
