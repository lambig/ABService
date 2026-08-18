package com.abservice.application.query.album;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import io.smallrye.mutiny.tuples.Tuple3;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ListAlbumsService（クランプ・結果組み立て）のテスト")
class ListAlbumsServiceTest {

    @Test
    @DisplayName("pageは負値なら0にクランプされる")
    void clampPageNegativeBecomesZero() {
        assertThat(ListAlbumsService.clampPage(-1)).isZero();
    }

    @Test
    @DisplayName("pageは0以上ならそのまま")
    void clampPagePositiveStaysSame() {
        assertThat(ListAlbumsService.clampPage(3)).isEqualTo(3);
    }

    @Test
    @DisplayName("sizeは1未満ならデフォルト20にクランプされる")
    void clampSizeBelowMinimumBecomesDefault() {
        assertThat(ListAlbumsService.clampSize(0)).isEqualTo(20);
        assertThat(ListAlbumsService.clampSize(-5)).isEqualTo(20);
    }

    @Test
    @DisplayName("sizeは100を超えると100にクランプされる")
    void clampSizeAboveMaximumBecomesMax() {
        assertThat(ListAlbumsService.clampSize(1000)).isEqualTo(100);
    }

    @Test
    @DisplayName("sizeは範囲内ならそのまま")
    void clampSizeWithinRangeStaysSame() {
        assertThat(ListAlbumsService.clampSize(50)).isEqualTo(50);
    }

    @Test
    @DisplayName("toResultはエンティティ一覧をViewへ変換しページ情報を組み立てる")
    void toResultBuildsResultFromTuple() {
        final var entity = new AlbumTableRecord();
        entity.setDomainId("0192f8a0-0000-7000-8000-000000000000");
        entity.setTitle("タイトル");
        entity.setReleaseDate(
                LocalDate.of(
                        2026,
                        1,
                        1));
        entity.setArtistDisplayName("アーティスト");

        final var tuple = Tuple3.of(
                List.of(entity),
                1L,
                1);

        final var result = ListAlbumsService.toResult(
                tuple,
                0,
                20);

        assertThat(result.items()).singleElement().satisfies(v -> assertThat(v.title()).isEqualTo("タイトル"));
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.totalPages()).isEqualTo(1);
    }
}
