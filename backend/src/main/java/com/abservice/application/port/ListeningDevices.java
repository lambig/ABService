package com.abservice.application.port;

import io.smallrye.mutiny.Uni;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 試聴端末の資格情報を永続化する境界。各更新は独立したトランザクションを完了してから応答する。
 * トークン本体は受け取らず、digestだけを扱う。認証時の照合もここを通り、要求のsessionに依存しない。
 */
public interface ListeningDevices {
    /** 内部で発行した未使用IDへ資格情報を作る。重複は更新せずfalse。 */
    Uni<Boolean> create(
            UUID id,
            String label,
            String tokenDigest,
            Instant expiresAt);

    Uni<Optional<ListeningDevice>> find(UUID id);

    /** 発行の新しい順。トークンは含まない。 */
    Uni<List<ListeningDevice>> list();

    /** 失効していない資格情報だけを失効させる。既に失効済み・未存在はfalse。 */
    Uni<Boolean> revoke(UUID id);

    /** DB時計で期限内かつ未失効の資格情報だけを返す。digestが一致しても期限切れ・失効済みはempty。 */
    Uni<Optional<ListeningDevice>> findActiveByDigest(String tokenDigest);
}
