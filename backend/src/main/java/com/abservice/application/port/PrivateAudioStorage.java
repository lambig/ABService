package com.abservice.application.port;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * 検査済み音源を不変の非公開実体として保存する境界。公開URLや任意の保管キーを受け渡さない。
 * ブロッキングI/Oを含むためイベントループ外で実行する。DBの確定状態とは別の保存段階であり、 write成功だけで配布可能としてはならない。
 */
public interface PrivateAudioStorage {
    /**
     * 検査済みスナップショットを、そのメタデータと一緒に未使用のIDへ保存する。
     * 同じ内容でも上書き・再確定は拒否する。開いたストリームは保存側が閉じ、snapshot自体は呼出元が閉じる。
     * 失敗時は書込成否が不明な場合がある。呼出元は自動削除/上書きせずfindで照合して復旧する。
     */
    void write(UUID audioId, InspectedAudio snapshot) throws IOException;

    /**
     * 保存済み実体のメタデータを返す。未存在だけがemptyで、アクセス拒否・破損・取得障害は失敗となる。
     * これは保存状態の照合であり、DBの確定・関連付け・配布認可を代替しない。
     */
    Optional<FlacMetadata> find(UUID audioId) throws IOException;
}
