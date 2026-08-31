package com.abservice.application.query.site;

import com.abservice.application.query.QueryService;
import com.abservice.application.query.site.model.SiteContentView;
import java.util.List;

/**
 * サイト文言の全件照会の結果
 *
 * <p>
 * 空リストも正常系です。文言が1件も登録されていない状態は「まだ入れていない」だけであり、利用側は該当する キーが無ければその区画を出しません。
 * </p>
 *
 * @param items
 *            サイト文言の Read Model のリスト（キーの昇順）
 */
public record ListSiteContentsResult(List<SiteContentView> items) implements QueryService.Result {
}
