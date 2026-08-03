package com.abservice.application.service.albumarticle;

import com.abservice.application.service.CommandService;
import org.jspecify.annotations.Nullable;

/**
 * アルバム記事作成コマンドの出力DTO
 *
 * <p>
 * 生成されたアルバム記事のうち、呼び出し側（presentation 層）が応答に必要とする最小限の情報を返します。
 * ドメインオブジェクトを直接公開しません。
 * </p>
 *
 * @param albumId
 *            対応するAlbum集約のID（本集約のID）
 * @param introShort
 *            お品書き用のショートコメント（nullable）
 * @param labelTag
 *            ラベルタグ（列挙子名。nullable）
 */
public record CreateAlbumArticleOutput(
        String albumId,
        @Nullable String introShort,
        @Nullable String labelTag) implements CommandService.Output {
}
