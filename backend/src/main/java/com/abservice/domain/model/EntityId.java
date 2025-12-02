package com.abservice.domain.model;

import com.fasterxml.uuid.Generators;

import java.util.UUID;

/**
 * エンティティIDの基底インターフェース
 *
 * <p>型パラメータTでどのエンティティのIDかを明示的に表現する。
 * これにより、IDの取り違えをより明確に防止できる。</p>
 *
 * <p>ドメインIDはUUIDv7形式の文字列で、インフラレベルのID（DB主キー）とは分離される。
 * UUIDv7は時系列順でソート可能なため、Comparableを実装できる。</p>
 *
 * @param <T> このIDが属するエンティティの型
 */
public interface EntityId<T extends DomainObject<T>> extends Comparable<EntityId<T>> {
    /**
     * IDの実際の値（UUIDv7形式の文字列）
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
     */
    static String generateUuidV7() {
        return Generators.timeBasedEpochGenerator().generate().toString();
    }

    /**
     * 文字列がUUID形式かどうかを検証する
     */
    static boolean isValidUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
