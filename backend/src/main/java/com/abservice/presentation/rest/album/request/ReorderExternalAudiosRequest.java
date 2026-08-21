package com.abservice.presentation.rest.album.request;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 外部音源順序変更リクエスト（REST の公開入力契約）
 *
 * @param orderedExternalAudioIds
 *            新しい順序で並べた外部音源IDのリスト（アルバムが持つ全件を1件ずつ含む必要がある）
 */
public record ReorderExternalAudiosRequest(@Nullable List<@Nullable String> orderedExternalAudioIds) {
}
