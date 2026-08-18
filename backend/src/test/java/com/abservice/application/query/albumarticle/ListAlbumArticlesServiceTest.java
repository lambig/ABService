package com.abservice.application.query.albumarticle;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.infrastructure.persistence.entity.AlbumArticleTableRecord;
import io.smallrye.mutiny.tuples.Tuple3;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ListAlbumArticlesService（クランプ・結果組み立て）のテスト")
class ListAlbumArticlesServiceTest {

    @Test
    @DisplayName("pageは負値なら0にクランプされる")
    void clampPageNegativeBecomesZero() {
        assertThat(ListAlbumArticlesService.clampPage(-1)).isZero();
    }

    @Test
    @DisplayName("pageは0以上ならそのまま")
    void clampPagePositiveStaysSame() {
        assertThat(ListAlbumArticlesService.clampPage(3)).isEqualTo(3);
    }

    @Test
    @DisplayName("sizeは1未満ならデフォルト20にクランプされる")
    void clampSizeBelowMinimumBecomesDefault() {
        assertThat(ListAlbumArticlesService.clampSize(0)).isEqualTo(20);
        assertThat(ListAlbumArticlesService.clampSize(-5)).isEqualTo(20);
    }

    @Test
    @DisplayName("sizeは100を超えると100にクランプされる")
    void clampSizeAboveMaximumBecomesMax() {
        assertThat(ListAlbumArticlesService.clampSize(1000)).isEqualTo(100);
    }

    @Test
    @DisplayName("sizeは範囲内ならそのまま")
    void clampSizeWithinRangeStaysSame() {
        assertThat(ListAlbumArticlesService.clampSize(50)).isEqualTo(50);
    }

    @Test
    @DisplayName("toResultはエンティティ一覧をViewへ変換しページ情報を組み立てる")
    void toResultBuildsResultFromTuple() {
        final var entity = new AlbumArticleTableRecord();
        entity.setDomainId("0192f8a0-0000-7000-8000-000000000000");
        entity.setIntroShort("ショートコメント");

        final var tuple = Tuple3.of(
                List.of(entity),
                1L,
                1);

        final var result = ListAlbumArticlesService.toResult(
                tuple,
                0,
                20);

        assertThat(result.items()).singleElement()
                .satisfies(v -> assertThat(v.introShort()).isEqualTo("ショートコメント"));
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.totalPages()).isEqualTo(1);
    }
}
