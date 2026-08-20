package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import java.util.List;

/**
 * アルバム非公開化コマンドの出力DTO
 *
 * @param albumId
 *            非公開化されたアルバムのID
 * @param title
 *            アルバムタイトル
 * @param published
 *            公開状態（非公開化成功時は常にfalse）
 * @param cascadeUnpublishedArticles
 *            当該アルバムを参照していたために連動して非公開化された記事の一覧（該当なしの場合は空）
 */
public record UnpublishAlbumOutput(
        String albumId,
        String title,
        boolean published,
        List<CascadeUnpublishedArticle> cascadeUnpublishedArticles) implements CommandService.Output {

    /**
     * カスケード非公開化された記事の要約情報
     *
     * @param articleId
     *            記事ID
     * @param title
     *            記事タイトル
     */
    public record CascadeUnpublishedArticle(String articleId, String title) {
    }
}
