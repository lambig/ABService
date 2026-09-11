package com.abservice.application.service.asset;

import com.abservice.application.port.AssetChangedDuringConfirmException;
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
 *
 * <p>
 * 確定は検査した実体の識別子（{@code entityTag}）を条件に取る。{@link #replacedAfterRead} は検査から確定までの
 * 隙間に受け入れ前が別の実体へ置き換わる状況を模し、条件の不一致を再現する。
 * </p>
 */
final class FakeAssetStorage implements AssetStorage {

    private static final Instant EXPIRES_AT = Instant.parse("2026-01-01T00:10:00Z");

    private static final String ENTITY_TAG = "\"stored-entity-tag\"";

    private static final String REPLACED_ENTITY_TAG = "\"replaced-entity-tag\"";

    private final @Nullable StoredAssetHead stored;
    private final boolean replacesAfterRead;
    private String currentEntityTag = ENTITY_TAG;
    private List<String> discardedKeys = List.of();
    private List<String> publishedKeys = List.of();
    private List<String> presignedKeys = List.of();

    private FakeAssetStorage(@Nullable StoredAssetHead stored, boolean replacesAfterRead) {
        this.stored = stored;
        this.replacesAfterRead = replacesAfterRead;
    }

    static FakeAssetStorage empty() {
        return new FakeAssetStorage(null, false);
    }

    static FakeAssetStorage holding(byte[] prefix, long totalBytes) {
        return new FakeAssetStorage(head(prefix, totalBytes), false);
    }

    static FakeAssetStorage replacedAfterRead(byte[] prefix, long totalBytes) {
        return new FakeAssetStorage(head(prefix, totalBytes), true);
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
                        .map(head -> truncated(head, length)))
                .onItem().invoke(this::replaceAfterRead);
    }

    @Override
    public Uni<Void> publish(String key, String entityTag) {
        return currentEntityTag.equals(entityTag)
                ? recordPublished(key)
                : Uni.createFrom().failure(new AssetChangedDuringConfirmException(key));
    }

    @Override
    public Uni<Boolean> isPublished(String key) {
        return Uni.createFrom().item(publishedKeys.contains(key));
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

    private Uni<Void> recordPublished(String key) {
        publishedKeys = appended(publishedKeys, key);
        return Uni.createFrom().voidItem();
    }

    private void replaceAfterRead() {
        currentEntityTag = replacesAfterRead
                ? REPLACED_ENTITY_TAG
                : currentEntityTag;
    }

    private static StoredAssetHead head(byte[] prefix, long totalBytes) {
        return new StoredAssetHead(
                prefix,
                totalBytes,
                null,
                ENTITY_TAG);
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
                head.contentType(),
                head.entityTag());
    }
}
