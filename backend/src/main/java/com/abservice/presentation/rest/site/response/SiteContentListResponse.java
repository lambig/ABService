package com.abservice.presentation.rest.site.response;

import java.util.List;

/**
 * サイト文言一覧のレスポンス（REST の公開出力契約）
 *
 * <p>
 * 全件を返すためページ情報を持たない。文言の件数は数十のままである見込みで、ページネーションを持たせる利点が ないため。
 * </p>
 *
 * @param items
 *            サイト文言のリスト（キーの昇順）
 */
public record SiteContentListResponse(List<SiteContentResponse> items) {
}
