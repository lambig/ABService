package com.abservice.presentation.rest.exception;

import com.abservice.application.exception.ConflictingEditException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * 古いフォームからの保存（編集世代の不一致）を 409 とするマッパー
 *
 * <p>
 * 競合の意味と応答は {@link ConflictingUpdateExceptionMapper} と同一にする。検出する位置が違うだけで
 * （こちらは保存前の世代の突き合わせ、あちらは flush 時の行数不一致）、呼び出し側の対処は「読み直して
 * 再試行」で変わらない。応答に世代の値は載せない（内部情報を出さない方針も同じ）。
 * </p>
 */
@Provider
public class ConflictingEditExceptionMapper implements ExceptionMapper<ConflictingEditException> {

    @Override
    public Response toResponse(ConflictingEditException exception) {
        return ConflictingUpdateExceptionMapper.conflict();
    }
}
