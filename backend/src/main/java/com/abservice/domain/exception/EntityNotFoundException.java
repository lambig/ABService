package com.abservice.domain.exception;

import org.jspecify.annotations.NonNull;

/**
 * リソース未存在を表す例外
 *
 * <p>
 * IDやキーで参照したエンティティ・集約が存在しない場合に用います。 presentation 層では 404 Not Found に変換します。
 * </p>
 */
public final class EntityNotFoundException extends DomainException {

    private static final String ERROR_CODE = "ENTITY_NOT_FOUND";

    /**
     * メッセージを指定して生成します。
     *
     * @param message
     *            人間可読なエラーメッセージ
     */
    public EntityNotFoundException(@NonNull String message) {
        super(ERROR_CODE, message);
    }

    /**
     * エンティティ名と識別子からメッセージを組み立てて生成します。
     *
     * @param entityName
     *            エンティティ名（例: {@code "Article"}）
     * @param id
     *            見つからなかった識別子
     * @return 生成された例外
     */
    public static @NonNull EntityNotFoundException of(@NonNull String entityName, @NonNull Object id) {
        return new EntityNotFoundException(entityName + " が見つかりません: id=" + id);
    }
}
