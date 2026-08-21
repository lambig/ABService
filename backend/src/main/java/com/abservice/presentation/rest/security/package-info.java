/**
 * HTTP 境界の認証・認可。管理操作は {@code Authorization: Bearer <APIキー>} を要求し、検証に成功した
 * リクエストへ管理者ロールを付与する。JSpecify {@code @NullMarked}: 既定で非 null、null 許容箇所のみ
 * {@code @Nullable} を明示（NullAway で強制）。
 */
@NullMarked
package com.abservice.presentation.rest.security;

import org.jspecify.annotations.NullMarked;
