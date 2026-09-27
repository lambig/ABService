package com.abservice.presentation.rest.audio.response;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 未設定はaudioId=null、revision=0。kindは常にalbum-crossfadeで曲番号とは対応しない。 */
public record AlbumCrossfadeResponse(String albumId, String kind, @Nullable UUID audioId, int revision) {
}
