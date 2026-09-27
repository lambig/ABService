package com.abservice.application.port;

import io.smallrye.mutiny.Uni;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 音源登録の技術的な状態を永続化する境界。各更新は独立したトランザクションを完了してから応答する。
 * 検査結果のcommit前に保存を開始しない。falseは未存在・期限切れ・状態または実体の競合で、DB障害は失敗として返す。
 */
public interface PrivateAudioRegistrations {
    /** 内部で発行した未使用IDを予約する。重複は更新せずfalse、過去の期限は拒否する。 */
    Uni<Boolean> create(UUID id, Instant expiresAt);

    Uni<Optional<PrivateAudioRegistration>> find(UUID id);

    /** 期限内のpendingへ検査結果を一度だけ保存する。成功後にだけ同じ検査スナップショットを保存する。 */
    Uni<Boolean> recordInspection(UUID id, FlacMetadata metadata);

    /** 保存先から照合した実測値が先行記録と全項目一致するときだけ確定する。再確定はfalse。 */
    Uni<Boolean> confirm(UUID id, FlacMetadata storedMetadata);

    /**
     * 期限切れpendingを上限付きで失効し、そのIDを返す。batchSizeは1〜1000。
     * inspectedには保存済み実体があり得るため対象にしない。実体削除・復旧・清掃のスケジュールは呼出元が担当する。
     */
    Uni<List<UUID>> expirePending(int batchSize);
}
