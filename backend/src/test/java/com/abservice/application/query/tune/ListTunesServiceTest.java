package com.abservice.application.query.tune;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.application.query.SortDirection;
import com.abservice.infrastructure.persistence.entity.TuneTableRecord;
import io.smallrye.mutiny.tuples.Tuple3;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ListTunesService（クランプ・結果組み立て）のテスト")
class ListTunesServiceTest {

    @Test
    @DisplayName("pageは負値なら0にクランプされる")
    void clampPageNegativeBecomesZero() {
        assertThat(
                new ListTunesQuery(
                        -1,
                        20,
                        null,
                        null).asValidated().page())
                .isZero();
    }

    @Test
    @DisplayName("pageは0以上ならそのまま")
    void clampPagePositiveStaysSame() {
        assertThat(
                new ListTunesQuery(
                        3,
                        20,
                        null,
                        null).asValidated().page())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("sizeは1未満ならデフォルト20にクランプされる")
    void clampSizeBelowMinimumBecomesDefault() {
        assertThat(
                new ListTunesQuery(
                        0,
                        0,
                        null,
                        null).asValidated().size())
                .isEqualTo(20);
        assertThat(
                new ListTunesQuery(
                        0,
                        -5,
                        null,
                        null).asValidated().size())
                .isEqualTo(20);
    }

    @Test
    @DisplayName("sizeは100を超えると100にクランプされる")
    void clampSizeAboveMaximumBecomesMax() {
        assertThat(
                new ListTunesQuery(
                        0,
                        1000,
                        null,
                        null).asValidated().size())
                .isEqualTo(100);
    }

    @Test
    @DisplayName("sizeは範囲内ならそのまま")
    void clampSizeWithinRangeStaysSame() {
        assertThat(
                new ListTunesQuery(
                        0,
                        50,
                        null,
                        null).asValidated().size())
                .isEqualTo(50);
    }

    @Test
    @DisplayName("toResultはエンティティ一覧をViewへ変換しページ情報を組み立てる")
    void toResultBuildsResultFromTuple() {
        final var entity = new TuneTableRecord();
        entity.setDomainId("0192f8a0-0000-7000-8000-000000000000");
        entity.setTitle("タイトル");
        entity.setTuneKind("TRAD");

        final var tuple = Tuple3.of(
                List.of(entity),
                1L,
                1);

        final var result = ListTunesService.toResult(
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
        final var validated = new ListTunesQuery(
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                "UPDATEDAT",
                "ASC")
                .asValidated();
        assertThat(validated.page()).isZero();
        assertThat(validated.size()).isEqualTo(100);
        assertThat(validated.sort().direction()).isEqualTo(SortDirection.ASC);
        assertThat(validated.sort().property()).isEqualTo("updatedAt");
    }

}
