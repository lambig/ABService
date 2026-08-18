package com.abservice.application.service.tune;

import com.abservice.application.service.CommandService;

/**
 * チューン削除コマンドの出力DTO
 *
 * <p>
 * べき等のため返す情報を持ちません。
 * </p>
 */
public record DeleteTuneOutput() implements CommandService.Output {
}
