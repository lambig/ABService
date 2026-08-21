/**
 * オブジェクトストレージ（S3互換）へのアダプタ。{@code application.port} のポートを実装する。開発は MinIO、本番は S3 を
 * endpoint-override で切り替える。JSpecify {@code @NullMarked}: 既定で非 null、null 許容箇所のみ
 * {@code @Nullable} を明示（NullAway で強制）。
 */
@NullMarked
package com.abservice.infrastructure.storage;

import org.jspecify.annotations.NullMarked;
