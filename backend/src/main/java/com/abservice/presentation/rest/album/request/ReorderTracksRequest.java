package com.abservice.presentation.rest.album.request;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * トラック順序変更リクエスト（REST の公開入力契約）
 *
 * @param orderedTrackIds
 *            新しい順序で並べたトラックIDのリスト（アルバムが持つ全トラックを1件ずつ含む必要がある）
 */
public record ReorderTracksRequest(@Nullable List<@Nullable String> orderedTrackIds) {
}
