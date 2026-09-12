package com.abservice.application.port;

/**
 * 検査した実体をそのキーへ確定できなかったことを表す
 *
 * <p>
 * 確定は2つの条件を同時に満たす場合だけ成立する。コピー元が検査したその実体であること、コピー先がまだ無いこと。
 * 受け入れ前のキーへは有効な署名付きURLで何度でも書き込めるため前者が崩れ得るし、同時に走った確定が先に配信対象を
 * 作れば後者が崩れる。どちらで崩れても、検査した実体をこのキーへ確定することはできない（#285）。
 * </p>
 *
 * <p>
 * 保管先の事情を表すポートの例外であり、HTTP やドメインの語彙は持たない。呼び出し側の対処は書き込みの競合と同じであり、
 * {@link com.abservice.application.service.asset.ConfirmAssetUploadService}
 * が競合として扱う。
 * </p>
 */
public final class AssetConfirmConflictException extends RuntimeException {

    /**
     * @param key
     *            アセットキー
     */
    public AssetConfirmConflictException(String key) {
        super(message(key));
    }

    /**
     * @param key
     *            アセットキー
     * @param cause
     *            保管先が条件の不成立を知らせた例外
     */
    public AssetConfirmConflictException(String key, Throwable cause) {
        super(
                message(key),
                cause);
    }

    private static String message(String key) {
        return "確定の条件を満たしませんでした（実体が置き換わったか、既に確定済み）: key=" + key;
    }
}
