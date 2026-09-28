package com.abservice.application.port;

import java.time.Instant;
import java.util.UUID;

/**
 * 確定済み音源の取得URLを期限付きで解決する境界。実体の存在・DBの確定状態・配布認可は検証しない。
 *
 * <p>
 * URLは Manifest の恒久的な識別子ではなく、端末の要求ごとに発行する。有効期限は設定の有効時間・署名資格情報の残存時間・
 * 呼出元が渡す上限（端末の資格情報の期限）のうち最も短いものになる。発行済みのURLは期限まで有効で、サーバー側から 取り消す手段はない。
 * </p>
 */
public interface PrivateAudioDownloads {
    /**
     * @param audioId
     *            確定済み実体の登録ID
     * @param notAfter
     *            この時刻を超えてURLを有効にしない
     * @return 実際の署名期限を含むURL
     */
    PresignedDownload presign(UUID audioId, Instant notAfter);
}
