package com.abservice.application.service.albumarticle;

import com.abservice.application.service.CommandService;
import org.jspecify.annotations.Nullable;

/**
 * アルバム記事更新コマンドの出力DTO
 *
 * <p>
 * 更新されたアルバム記事のうち、呼び出し側（presentation 層）が応答に必要とする最小限の情報を返します。
 * ドメインオブジェクトを直接公開しません。
 * </p>
 *
 * @param albumId
 *            アルバム記事ID（対応するAlbum集約のID）
 * @param introShort
 *            お品書き用のショートコメント（nullable）
 * @param labelTag
 *            ラベルタグ（列挙子名。nullable）
 */
public record UpdateAlbumArticleOutput(
        String albumId,
        @Nullable String introShort,
        @Nullable String labelTag) implements CommandService.Output {
}
