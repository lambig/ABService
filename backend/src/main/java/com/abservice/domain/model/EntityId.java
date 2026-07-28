package com.abservice.domain.model;

import com.fasterxml.uuid.Generators;
import java.util.Optional;

/**
 * エンティティIDの基底インターフェース
 *
 * <p>
 * 型パラメータTでどのエンティティのIDかを明示的に表現する。 これにより、IDの取り違えをより明確に防止できる。
 * </p>
 *
 * <p>
 * ドメインIDはUUIDv7形式の文字列で、インフラレベルのID（DB主キー）とは分離される。
 * UUIDv7は時系列順でソート可能なため、Comparableを実装できる。
 * </p>
 *
 * @param <T>
 *            このIDが属するエンティティの型
 */
public interface EntityId<T extends DomainObject<T>> extends Comparable<EntityId<T>> {
    /**
     * IDの実際の値（UUIDv7形式の文字列）
     *
     * @return ID値
     */
    String value();

    /**
     * デフォルト実装：値による比較
     */
    @Override
    default int compareTo(EntityId<T> other) {
        return this.value().compareTo(other.value());
    }

    /**
     * UUID v7を生成する
     *
     * @return 生成されたUUID v7文字列
     */
    static String generateUuidV7() {
        return Generators.timeBasedEpochGenerator().generate().toString();
    }

    /**
     * 文字列がUUID形式（正準形）かどうかを検証する
     *
     * @param value
     *            検証対象の文字列
     * @return UUID形式であればtrue
     */
    static boolean isValidUuid(String value) {
        return Optional.ofNullable(value)
                .filter(v -> v.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
                .isPresent();
    }
}
