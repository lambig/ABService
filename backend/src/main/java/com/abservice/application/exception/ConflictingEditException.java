package com.abservice.application.exception;

/**
 * 編集を始めた時点より後に、別の操作が同じものを保存していたことを表す例外
 *
 * <p>
 * 全項目置換の更新は、届いた値をそのまま最新の状態へ適用する。したがって「読んだ時点」を条件として持ち込まない限り、
 * 古いフォームからの保存が、その間に入った別の保存を消す（#287）。この例外は、持ち込まれた世代
 * （{@code expectedRevision}）が保存直前に読んだ世代と違ったことを表す。
 * </p>
 *
 * <p>
 * ドメインの例外階層（{@code domain.exception}）には置かない。世代は作品についての事実ではなく、
 * <b>更新の契約</b>である（{@code AlbumRepository.Revision}）。presentation 層では、flush
 * 時に検出する 楽観ロックの競合と同じ 409 へ変換する（呼び出し側の対処が「読み直して再試行」で同じであるため、
 * 別のコードにして区別させる意味がない）。
 * </p>
 */
public final class ConflictingEditException extends RuntimeException {

    /**
     * 対象と、突き合わせに使った世代を添えて生成します。
     *
     * @param message
     *            人間可読なエラーメッセージ（応答には載せない。ログのためだけに持つ）
     */
    public ConflictingEditException(String message) {
        super(message);
    }
}
