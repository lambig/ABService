package com.abservice.application.service.site;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.site.SiteContent;
import com.abservice.domain.model.vo.common.MarkupContent;
import com.abservice.domain.model.vo.site.SiteContentKey;
import com.abservice.domain.repository.site.SiteContentRepository;
import com.abservice.lib.Result;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;

/**
 * サイト文言の登録・更新コマンドサービス
 *
 * <p>
 * キー単位の upsert です。同じキーが既にあれば文言を差し替え、無ければ新しく作ります。文言は「そのキーの
 * 現在の内容」であって履歴を持たないため、登録と更新を別の操作に分ける意味がありません。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class UpsertSiteContentService implements CommandService<UpsertSiteContentInput, UpsertSiteContentOutput> {

    private final SiteContentRepository siteContentRepository;

    @WithTransaction
    @Override
    public Uni<UpsertSiteContentOutput> execute(UpsertSiteContentInput input) {
        return Uni.createFrom()
                .item(() -> validate(input))
                .flatMap(this::upserted)
                .map(UpsertSiteContentService::toOutput);
    }

    private record Parsed(SiteContentKey key, MarkupContent content) {
    }

    private static Parsed validate(UpsertSiteContentInput input) {
        return Result.zip(
                SiteContentKey.fromInput(input.key()),
                MarkupContent.fromInput(input.content(), input.contentFormat()),
                Parsed::new)
                .resolve(ValidationException::new);
    }

    /**
     * 同じキーが既にあれば文言を差し替え、無ければ新しく作って保存する。
     *
     * @param valid
     *            検証済みのキーと文言
     * @return 保存後のサイト文言
     */
    private Uni<SiteContent> upserted(Parsed valid) {
        return siteContentRepository.findByKey(valid.key())
                .onItem().ifNotNull().transform(existing -> existing.withContent(valid.content()))
                .onItem().ifNull().continueWith(() -> SiteContent.create(valid.key(), valid.content()))
                .flatMap(siteContentRepository::save);
    }

    private static UpsertSiteContentOutput toOutput(SiteContent siteContent) {
        return new UpsertSiteContentOutput(
                siteContent.key().value(),
                siteContent.content().content(),
                siteContent.content().format().name());
    }
}
