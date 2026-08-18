package com.abservice.application.query.albumarticle;

import com.abservice.application.query.QueryService;
import com.abservice.application.query.albumarticle.model.AlbumArticleView;
import java.util.List;

/**
 * アルバム記事一覧照会の結果
 *
 * <p>
 * 一覧は空リストも正常系のため、{@link GetAlbumArticleResult} のような Found/NotFound の分岐は不要で 単一の
 * record として表現する。
 * </p>
 *
 * @param items
 *            アルバム記事の Read Model のリスト（このページ分）
 * @param page
 *            ページ番号（0始まり、クランプ後の値）
 * @param size
 *            1ページの件数（クランプ後の値）
 * @param totalElements
 *            全件数
 * @param totalPages
 *            総ページ数
 */
public record ListAlbumArticlesResult(
        List<AlbumArticleView> items,
        int page,
        int size,
        long totalElements,
        int totalPages) implements QueryService.Result {
}
