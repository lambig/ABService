package com.abservice.application.query.site;

import com.abservice.application.query.QueryService;

/**
 * サイト文言の全件照会のクエリ
 *
 * <p>
 * 条件を持ちません。文言は全件を1リクエストで返します（件数は数十のままである見込みで、ページネーションや キー指定の取得を持たせる利点がない）。
 * </p>
 *
 * <p>
 * 要求元（{@code Audience}）も持ちません。公開向けと管理向けで返す項目に差がないためです。差が出た時点で 追加します。
 * </p>
 */
public record ListSiteContentsQuery() implements QueryService.Query {
}
