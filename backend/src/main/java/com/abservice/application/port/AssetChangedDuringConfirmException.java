package com.abservice.application.port;

/**
 * 検査した実体が、確定するまでの間に別のものへ置き換わっていたことを表す
 *
 * <p>
 * 受け入れ前のキーへは有効な署名付きURLで何度でも書き込めるため、検査から確定までの隙間に別の実体が置かれ得る。確定は
 * 検査した実体を条件にしており、置き換わっていればここで失敗する（#285）。
 * </p>
 *
 * <p>
 * 保管先の事情を表すポートの例外であり、HTTP やドメインの語彙は持たない。呼び出し側の対処は「上げ直して確定をやり直す」で
 * 書き込みの競合と同じであり、{@link com.abservice.application.service.asset.ConfirmAssetUploadService}
 * が競合として扱う。
 * </p>
 */
public final class AssetChangedDuringConfirmException extends RuntimeException {

    /**
     * @param key
     *            アセットキー
     */
    public AssetChangedDuringConfirmException(String key) {
        super("検査した実体が確定までの間に置き換わりました: key=" + key);
    }

    /**
     * @param key
     *            アセットキー
     * @param cause
     *            保管先が条件の不一致を知らせた例外
     */
    public AssetChangedDuringConfirmException(String key, Throwable cause) {
        super(
                "検査した実体が確定までの間に置き換わりました: key=" + key,
                cause);
    }
}
