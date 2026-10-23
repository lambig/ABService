/**
 * 所有する集約によらず意味と検証が一定の値。個別の集約・VO・サービスへは依存しない。
 * 共有の可否は利用数ではなく責務で決め、外部ライブラリへの抽出は別に判断する。 JSpecify {@code @NullMarked}: 既定で非
 * null、null 許容箇所のみ {@code @Nullable} を明示（NullAway で強制）。
 */
@NullMarked
package com.abservice.domain.model.vo.common;

import org.jspecify.annotations.NullMarked;
