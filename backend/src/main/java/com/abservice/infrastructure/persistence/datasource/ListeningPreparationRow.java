package com.abservice.infrastructure.persistence.datasource;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** 準備一覧に必要な状態だけを結合取得する。保存場所や資格情報を含めない。 */
public record ListeningPreparationRow(String albumId, String title, @Nullable Instant publishedAt,
        @Nullable String audioState) {
}
