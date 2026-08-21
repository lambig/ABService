/**
 * アプリケーション層が外部システムへ要求する能力（ポート）。実装（アダプタ）は infrastructure 層に置き、依存の向きを
 * infrastructure → application に保つ。ドメイン概念でない技術的リソース（オブジェクトストレージ等）の抽象はここに置く
 * （ドメインの永続化は {@code domain.repository} が担う）。JSpecify {@code @NullMarked}: 既定で非
 * null、null 許容箇所のみ {@code @Nullable} を明示（NullAway で強制）。
 */
@NullMarked
package com.abservice.application.port;

import org.jspecify.annotations.NullMarked;
