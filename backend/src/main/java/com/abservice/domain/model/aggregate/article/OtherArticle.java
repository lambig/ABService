package com.abservice.domain.model.aggregate.article;

import com.abservice.domain.model.AggregateFactory;
import com.abservice.domain.model.DomainConstructor;
import com.abservice.domain.model.DomainFactory;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.lib.ErrorResult;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

/**
 * その他の記事
 *
 * <p>
 * 種別に固有の項目を持たず、{@link ArticleCore} が持つ共通状態だけで成立します。
 * </p>
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class OtherArticle implements Article {
    /** 種別によらず共通する状態 */
    @EqualsAndHashCode.Include
    @NonNull
    private final ArticleCore core;

    /** core必須違反時のエラー */
    private static final ErrorResult CORE_REQUIRED_ERROR = new ErrorResult(
            "core",
            "Article core cannot be null",
            "ARTICLE_CORE_REQUIRED");

    @DomainConstructor
    private OtherArticle(@NonNull ArticleCore core) {
        this.core = core;
    }

    @DomainFactory
    private static @NonNull OtherArticle factory(@Nullable ArticleCore core) {
        return Policy.<Stub>of(
                self -> self.core() != null,
                CORE_REQUIRED_ERROR)
                .verify(new Stub(core), Stub::asOtherArticle)
                .resolve(Policy::illegalArgument);
    }

    @NullUnmarked
    private record Stub(ArticleCore core) {

        @AggregateFactory
        @NonNull
        OtherArticle asOtherArticle() {
            return new OtherArticle(Objects.requireNonNull(core));
        }
    }

    /**
     * 共通状態からその他の記事を組み立てます。
     *
     * @param core
     *            共通状態
     * @return その他の記事
     */
    static @NonNull OtherArticle of(@NonNull ArticleCore core) {
        return OtherArticle.factory(core);
    }

    @Override
    public @NonNull OtherArticle withCore(@NonNull ArticleCore newCore) {
        return OtherArticle.factory(newCore);
    }

    @Override
    public @NonNull ArticleType articleType() {
        return ArticleType.OTHER;
    }
}
