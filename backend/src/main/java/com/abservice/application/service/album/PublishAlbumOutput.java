package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;

/**
 * アルバム公開コマンドの出力DTO
 *
 * @param albumId
 *            公開されたアルバムのID
 * @param title
 *            アルバムタイトル
 * @param published
 *            公開状態（公開成功時は常にtrue）
 */
public record PublishAlbumOutput(
        String albumId,
        String title,
        boolean published) implements CommandService.Output {
}
