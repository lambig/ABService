package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import java.util.List;

/**
 * アルバム削除コマンドの出力DTO
 *
 * <p>
 * 削除自体はべき等で対象の存在有無を問わないため、返すのは削除に伴って影響を受けた記事の情報だけです。参照していた記事は
 * 参照を失効させ、公開中だったものは非公開へ戻します（フロントへの通知に使います）。
 * </p>
 *
 * @param affectedArticles
 *            当該アルバムを参照していたために影響を受けた記事の一覧（該当なしの場合は空）
 */
public record DeleteAlbumOutput(List<AffectedArticle> affectedArticles) implements CommandService.Output {

    /**
     * アルバム削除の影響を受けた記事の要約情報
     *
     * @param articleId
     *            記事ID
     * @param title
     *            記事タイトル
     * @param unpublished
     *            公開中だったため非公開へ戻したか
     */
    public record AffectedArticle(String articleId, String title, boolean unpublished) {
    }
}
