/**
 * アルバム集約のアプリケーションサービス（Query）。JSpecify {@code @NullMarked}: 既定で非 null、null
 * 許容箇所のみ {@code @Nullable} を明示（NullAway で強制）。
 *
 * <p>
 * CQRS の Read 側。{@code infrastructure.persistence.datasource} 経由で読み取り、Read
 * Model DTO （{@code model} サブパッケージ）を返す。書き込み側の Repository・ドメインは経由しない。
 * </p>
 */
@NullMarked
package com.abservice.application.query.album;

import org.jspecify.annotations.NullMarked;
