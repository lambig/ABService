package com.abservice.domain.model.vo.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.lib.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ArticleTitle値オブジェクトのテスト")
class ArticleTitleTest {

    @DisplayName("有効なタイトルで生成できる")
    @Test
    void testCreateValidTitle() {
        final ArticleTitle title = ArticleTitle.of("素敵な記事");
        assertThat(title.value()).isEqualTo("素敵な記事");
    }

    @DisplayName("最大長500文字のタイトルで生成できる")
    @Test
    void testCreateTitleMaxLength() {
        final ArticleTitle title = ArticleTitle.of("a".repeat(500));
        assertThat(title.value()).hasSize(500);
    }

    @DisplayName("nullのタイトルは例外となる")
    @Test
    void testCreateTitleNull() {
        assertThatThrownBy(() -> ArticleTitle.of(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("記事タイトルは必須です");
    }

    @DisplayName("空文字のタイトルは例外となる")
    @Test
    void testCreateTitleEmpty() {
        assertThatThrownBy(() -> ArticleTitle.of("")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("記事タイトルは必須です");
    }

    @DisplayName("空白のみのタイトルは例外となる")
    @Test
    void testCreateTitleBlank() {
        assertThatThrownBy(() -> ArticleTitle.of("   ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("記事タイトルは必須です");
    }

    @DisplayName("501文字以上のタイトルは例外となる")
    @Test
    void testCreateTitleTooLong() {
        assertThatThrownBy(() -> ArticleTitle.of("a".repeat(501))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("記事タイトルは500文字以内です");
    }

    @DisplayName("同じ値同士はequivalentToがtrueとなる")
    @Test
    void testEquivalentToSame() {
        assertThat(ArticleTitle.of("記事タイトル").equivalentTo(ArticleTitle.of("記事タイトル"))).isTrue();
    }

    @DisplayName("異なる値同士はequivalentToがfalseとなる")
    @Test
    void testEquivalentToDifferent() {
        assertThat(ArticleTitle.of("記事A").equivalentTo(ArticleTitle.of("記事B"))).isFalse();
    }

    @DisplayName("nullとのequivalentToはfalseとなる")
    @Test
    void testEquivalentToNull() {
        assertThat(ArticleTitle.of("記事タイトル").equivalentTo(null)).isFalse();
    }

    @Nested
    @DisplayName("fromInput（外部入力からの生成）")
    class FromInputTest {

        @Test
        @DisplayName("有効なタイトルで成功する")
        void validTitleShouldSucceed() {
            final Result<ArticleTitle> result = ArticleTitle.fromInput("素敵な記事");
            assertThat(result.resolve().value()).isEqualTo("素敵な記事");
        }

        @Test
        @DisplayName("nullは必須エラーになる")
        void nullShouldFailAsRequired() {
            final Result<ArticleTitle> result = ArticleTitle.fromInput(null);
            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<ArticleTitle>) result).errors()).anySatisfy(e -> {
                assertThat(e.field()).isEqualTo("title");
                assertThat(e.code()).isEqualTo("ARTICLE_TITLE_REQUIRED");
            });
        }

        @Test
        @DisplayName("空白のみは必須エラーになる")
        void blankShouldFailAsRequired() {
            final Result<ArticleTitle> result = ArticleTitle.fromInput("   ");
            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<ArticleTitle>) result).errors())
                    .anySatisfy(e -> assertThat(e.code()).isEqualTo("ARTICLE_TITLE_REQUIRED"));
        }

        @Test
        @DisplayName("501文字以上は長さ超過エラーになる")
        void tooLongShouldFailAsTooLong() {
            final Result<ArticleTitle> result = ArticleTitle.fromInput("a".repeat(501));
            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<ArticleTitle>) result).errors()).singleElement()
                    .satisfies(e -> assertThat(e.code()).isEqualTo("ARTICLE_TITLE_TOO_LONG"));
        }
    }
}
