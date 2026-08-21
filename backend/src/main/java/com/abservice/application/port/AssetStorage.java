package com.abservice.application.port;

import io.smallrye.mutiny.Uni;
import java.util.Optional;

/**
 * アセット（画像等のバイナリ）の保管先が満たすべき能力
 *
 * <p>
 * アップロードはクライアントから保管先へ直接行われる（署名付きURL）ため、本ポートはバイト列を受け取らない。
 * アプリケーション層はURLの発行と、アップロード後の実体検査・破棄のみを要求する。
 * </p>
 */
public interface AssetStorage {

    /**
     * 指定キーへのアップロードを許可する署名付きURLを発行します。
     *
     * @param key
     *            保管先のキー
     * @param contentType
     *            アップロードを許可する Content-Type（URLに束縛する）
     * @return 署名付きURLと有効期限
     */
    Uni<PresignedUpload> presignUpload(String key, String contentType);

    /**
     * 保管済みアセットの先頭バイト列とメタ情報を読み出します。
     *
     * @param key
     *            保管先のキー
     * @param length
     *            読み出す先頭バイト数
     * @return 実体が存在すればその先頭バイト列とメタ情報、存在しなければ空
     */
    Uni<Optional<StoredAssetHead>> readHead(String key, int length);

    /**
     * 保管済みアセットを削除します。存在しないキーの削除は成功として扱います。
     *
     * @param key
     *            保管先のキー
     * @return 完了
     */
    Uni<Void> delete(String key);
}
