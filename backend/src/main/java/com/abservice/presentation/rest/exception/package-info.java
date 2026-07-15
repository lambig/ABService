/**
 * REST の例外→HTTP 変換と RFC 9457 Problem Details 表現。JSpecify {@code @NullMarked}:
 * 既定で非 null、 null 許容箇所のみ {@code @Nullable} を明示（NullAway で強制）。
 */
@NullMarked
package com.abservice.presentation.rest.exception;

import org.jspecify.annotations.NullMarked;
