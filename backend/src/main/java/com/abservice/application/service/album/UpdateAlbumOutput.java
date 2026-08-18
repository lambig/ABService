package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;

/**
 * アルバム更新コマンドの出力DTO
 *
 * <p>
 * 更新されたアルバムのうち、呼び出し側（presentation 層）が応答に必要とする最小限の情報を返します。 ドメインオブジェクトを直接公開しません。
 * </p>
 *
 * @param albumId
 *            アルバムID（UUIDv7形式の文字列）
 * @param title
 *            アルバムタイトル
 * @param releaseDate
 *            リリース日（ISO-8601形式の文字列）
 * @param artistDisplayName
 *            アーティスト表示名
 */
public record UpdateAlbumOutput(
        String albumId,
        String title,
        String releaseDate,
        String artistDisplayName) implements CommandService.Output {
}
