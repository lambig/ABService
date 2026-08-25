package com.abservice.application.service.asset;

import com.abservice.application.port.AssetStorage;
import com.abservice.application.port.PresignedUpload;
import com.abservice.application.port.StoredAssetHead;
import io.smallrye.mutiny.Uni;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * アセット保管先のテスト代替
 *
 * <p>
 * 受け入れ前の実体を1件だけ持ち、確定・破棄されたキーを記録する。DI・実ストレージを伴わない単体テストで用いる。
 * </p>
 */
final class FakeAssetStorage implements AssetStorage {

    private static final Instant EXPIRES_AT = Instant.parse("2026-01-01T00:10:00Z");

    private final @Nullable StoredAssetHead stored;
    private List<String> discardedKeys = List.of();
    private List<String> publishedKeys = List.of();
    private List<String> presignedKeys = List.of();

    private FakeAssetStorage(@Nullable StoredAssetHead stored) {
        this.stored = stored;
    }

    static FakeAssetStorage empty() {
        return new FakeAssetStorage(null);
    }

    static FakeAssetStorage holding(byte[] prefix, long totalBytes) {
        return new FakeAssetStorage(
                new StoredAssetHead(
                        prefix,
                        totalBytes,
                        null));
    }

    @Override
    public Uni<PresignedUpload> presignUpload(String key, String contentType) {
        presignedKeys = appended(presignedKeys, key);
        return Uni.createFrom().item(
                new PresignedUpload(
                        "https://storage.example.com/" + key + "?signature=stub",
                        EXPIRES_AT));
    }

    @Override
    public Uni<Optional<StoredAssetHead>> readHead(String key, int length) {
        return Uni.createFrom().item(
                Optional.ofNullable(stored)
                        .map(head -> truncated(head, length)));
    }

    @Override
    public Uni<Void> publish(String key) {
        publishedKeys = appended(publishedKeys, key);
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Void> discard(String key) {
        discardedKeys = appended(discardedKeys, key);
        return Uni.createFrom().voidItem();
    }

    List<String> discardedKeys() {
        return discardedKeys;
    }

    List<String> publishedKeys() {
        return publishedKeys;
    }

    List<String> presignedKeys() {
        return presignedKeys;
    }

    private static List<String> appended(List<String> keys, String key) {
        return Stream.concat(
                keys.stream(),
                Stream.of(key))
                .toList();
    }

    static Instant expiresAt() {
        return EXPIRES_AT;
    }

    private static StoredAssetHead truncated(StoredAssetHead head, int length) {
        return new StoredAssetHead(
                Arrays.copyOf(head.prefix(), Math.min(length, head.prefix().length)),
                head.totalBytes(),
                head.contentType());
    }
}
