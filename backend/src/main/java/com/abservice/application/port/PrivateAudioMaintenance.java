package com.abservice.application.port;

import io.smallrye.mutiny.Uni;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** DB時計で期限を判定する登録の照合・終了境界。保存実体の削除権限は持たない。 */
public interface PrivateAudioMaintenance {
    Uni<List<UUID>> staleInspected(Duration retention, int batchSize);

    /** HEADで未存在を確認した後にだけ呼ぶ。遅延PUTはあり得るため検査記録を保持する。 */
    Uni<Boolean> abandonInspected(UUID id, Duration retention);

    /** 明示的な復旧では、終了済み登録でも全実測値が一致すれば確定できる。 */
    Uni<Boolean> confirmRecovered(UUID id, FlacMetadata metadata);
}
