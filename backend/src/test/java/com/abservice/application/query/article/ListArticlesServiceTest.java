package com.abservice.application.query.article;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.application.query.SortDirection;
import com.abservice.application.query.Audience;
import com.abservice.infrastructure.persistence.entity.ArticleTableRecord;
import io.smallrye.mutiny.tuples.Tuple3;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ListArticlesService（クランプ・結果組み立て）のテスト")
class ListArticlesServiceTest {

    @Test
    @DisplayName("pageは負値なら0にクランプされる")
    void clampPageNegativeBecomesZero() {
        assertThat(
                new ListArticlesQuery(
                        -1,
                        20,
                        Audience.ADMIN,
                        null,
                        null,
                        null,
                        null).asValidated().page())
                .isZero();
    }

    @Test
    @DisplayName("pageは0以上ならそのまま")
    void clampPagePositiveStaysSame() {
        assertThat(
                new ListArticlesQuery(
                        3,
                        20,
                        Audience.ADMIN,
                        null,
                        null,
                        null,
                        null).asValidated().page())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("sizeは1未満ならデフォルト20にクランプされる")
    void clampSizeBelowMinimumBecomesDefault() {
        assertThat(
                new ListArticlesQuery(
                        0,
                        0,
                        Audience.ADMIN,
                        null,
                        null,
                        null,
                        null).asValidated().size())
                .isEqualTo(20);
        assertThat(
                new ListArticlesQuery(
                        0,
                        -5,
                        Audience.ADMIN,
                        null,
                        null,
                        null,
                        null).asValidated().size())
                .isEqualTo(20);
    }

    @Test
    @DisplayName("sizeは100を超えると100にクランプされる")
    void clampSizeAboveMaximumBecomesMax() {
        assertThat(
                new ListArticlesQuery(
                        0,
                        1000,
                        Audience.ADMIN,
                        null,
                        null,
                        null,
                        null).asValidated().size())
                .isEqualTo(100);
    }

    @Test
    @DisplayName("sizeは範囲内ならそのまま")
    void clampSizeWithinRangeStaysSame() {
        assertThat(
                new ListArticlesQuery(
                        0,
                        50,
                        Audience.ADMIN,
                        null,
                        null,
                        null,
                        null).asValidated().size())
                .isEqualTo(50);
    }

    @Test
    @DisplayName("toResultはエンティティ一覧をViewへ変換しページ情報を組み立てる")
    void toResultBuildsResultFromTuple() {
        final var entity = new ArticleTableRecord();
        entity.setDomainId("0192f8a0-0000-7000-8000-000000000000");
        entity.setArticleType("NOTE");
        entity.setTitle("タイトル");
        entity.setBodyFormat("PLAIN_TEXT");

        final var tuple = Tuple3.of(
                List.of(entity),
                1L,
                1);

        final var result = ListArticlesService.toResult(
                tuple,
                0,
                20);

        assertThat(result.items()).singleElement().satisfies(v -> assertThat(v.title()).isEqualTo("タイトル"));
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("補正したページと解決済みの並び順を照会条件へ渡す")
    void normalizedConditionsKeepFiltersAndSort() {
        final var validated = new ListArticlesQuery(
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                Audience.ADMIN,
                "UPDATEDATBUSINESS",
                "ASC",
                "not-a-uuid",
                false).asValidated();
        assertThat(validated.page()).isZero();
        assertThat(validated.size()).isEqualTo(100);
        assertThat(validated.sort().direction()).isEqualTo(SortDirection.ASC);
        assertThat(validated.sort().property()).isEqualTo("updatedAtBusiness");
        assertThat(validated.albumId()).isEqualTo("not-a-uuid");
        assertThat(validated.publicFlag()).isFalse();
    }

}
