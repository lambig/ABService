package com.abservice.application.service.tune;

import com.abservice.application.service.CommandService;
import org.jspecify.annotations.Nullable;

/**
 * チューン削除コマンドの入力DTO
 *
 * @param tuneId
 *            削除対象のチューンID
 */
public record DeleteTuneInput(@Nullable String tuneId) implements CommandService.Input {
}
