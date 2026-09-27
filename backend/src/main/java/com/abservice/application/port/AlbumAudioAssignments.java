package com.abservice.application.port;

import io.smallrye.mutiny.Uni;
import java.util.Optional;
import java.util.UUID;

/** Albumのクロスフェード選択。曲別音源はこの枠へ代入せず、別の再生種別として追加する。 */
public interface AlbumAudioAssignments {
    Uni<Optional<Assignment>> findCrossfade(String albumId);

    /** 呼出元がAlbumの参照を主張した同一トランザクションで、確定音源へ世代条件付きで置換する。 */
    Uni<Boolean> replaceCrossfade(
            String albumId,
            UUID audioId,
            int expectedRevision);

    record Assignment(UUID audioId, int revision) {
    }
}
