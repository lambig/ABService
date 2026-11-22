package com.abservice.application.service.site;

import com.abservice.application.service.CommandService;
import com.abservice.application.service.InputPolicy;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.markup.MarkupContent;
import com.abservice.domain.model.vo.site.SiteContentKey;
import com.abservice.lib.Result;
import org.jspecify.annotations.Nullable;

/**
 * サイト文言の登録・更新コマンドの入力DTO
 *
 * @param key
 *            どの文言かを指すキー
 * @param content
 *            文言の本文
 * @param contentFormat
 *            文言のマークアップ形式（列挙子名）
 */
public record UpsertSiteContentInput(
        @Nullable String key,
        @Nullable String content,
        @Nullable String contentFormat) implements CommandService.Input<UpsertSiteContentInput> {
    @Override
    public Policy<UpsertSiteContentInput> policy() {
        return new InputPolicy<>(UpsertSiteContentInput::parse);
    }

    record Parsed(SiteContentKey key, MarkupContent content) {
    }

    static Result<Parsed> parse(UpsertSiteContentInput input) {
        return Result.zip(
                SiteContentKey.fromInput(input.key())
                        .mapErrorFields(field -> "key"),
                MarkupContent.fromInput(input.content(), input.contentFormat())
                        .mapErrorFields(
                                field -> "format".equals(field)
                                        ? "contentFormat"
                                        : "content"),
                Parsed::new);
    }

}
