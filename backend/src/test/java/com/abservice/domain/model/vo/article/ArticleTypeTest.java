package com.abservice.domain.model.vo.article;

import com.abservice.lib.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleTypeTest {

    @Test
    void testEnumValues() {
        assertThat(ArticleType.values()).containsExactly(ArticleType.ALBUM, ArticleType.NOTE, ArticleType.NEWS,
                ArticleType.EVENT, ArticleType.OTHER);
    }

    @Test
    void testValueOfALBUM() {
        ArticleType type = ArticleType.valueOf("ALBUM");
        assertThat(type).isEqualTo(ArticleType.ALBUM);
    }

    @Test
    void testValueOfNOTE() {
        ArticleType type = ArticleType.valueOf("NOTE");
        assertThat(type).isEqualTo(ArticleType.NOTE);
    }

    @Test
    void testValueOfNEWS() {
        ArticleType type = ArticleType.valueOf("NEWS");
        assertThat(type).isEqualTo(ArticleType.NEWS);
    }

    @Test
    void testValueOfEVENT() {
        ArticleType type = ArticleType.valueOf("EVENT");
        assertThat(type).isEqualTo(ArticleType.EVENT);
    }

    @Test
    void testValueOfOTHER() {
        ArticleType type = ArticleType.valueOf("OTHER");
        assertThat(type).isEqualTo(ArticleType.OTHER);
    }

    @Test
    void testName() {
        assertThat(ArticleType.ALBUM.name()).isEqualTo("ALBUM");
        assertThat(ArticleType.NOTE.name()).isEqualTo("NOTE");
        assertThat(ArticleType.NEWS.name()).isEqualTo("NEWS");
        assertThat(ArticleType.EVENT.name()).isEqualTo("EVENT");
        assertThat(ArticleType.OTHER.name()).isEqualTo("OTHER");
    }

    @Test
    void testEnumCount() {
        assertThat(ArticleType.values()).hasSize(5);
    }

    @Nested
    @DisplayName("fromInput（外部入力からの生成）")
    class FromInputTest {

        @Test
        @DisplayName("有効な列挙子名で成功する")
        void validNameShouldSucceed() {
            // Act
            Result<ArticleType> result = ArticleType.fromInput("ALBUM");

            // Assert
            assertThat(result.resolve()).isEqualTo(ArticleType.ALBUM);
        }

        @Test
        @DisplayName("前後の空白を許容する")
        void surroundingWhitespaceIsTrimmed() {
            // Act
            Result<ArticleType> result = ArticleType.fromInput("  NOTE  ");

            // Assert
            assertThat(result.resolve()).isEqualTo(ArticleType.NOTE);
        }

        @Test
        @DisplayName("nullは必須エラーになる")
        void nullShouldFailAsRequired() {
            // Act
            Result<ArticleType> result = ArticleType.fromInput(null);

            // Assert
            assertThat(result).isInstanceOf(Result.Failure.class);
            var errors = ((Result.Failure<ArticleType>) result).errors();
            assertThat(errors).singleElement().satisfies(e -> {
                assertThat(e.field()).isEqualTo("articleType");
                assertThat(e.code()).isEqualTo("ARTICLE_TYPE_REQUIRED");
            });
        }

        @Test
        @DisplayName("空白のみは必須エラーになる")
        void blankShouldFailAsRequired() {
            // Act
            Result<ArticleType> result = ArticleType.fromInput("   ");

            // Assert
            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<ArticleType>) result).errors()).singleElement()
                    .satisfies(e -> assertThat(e.code()).isEqualTo("ARTICLE_TYPE_REQUIRED"));
        }

        @Test
        @DisplayName("未知の値は不正エラーになる")
        void unknownValueShouldFailAsInvalid() {
            // Act
            Result<ArticleType> result = ArticleType.fromInput("UNKNOWN");

            // Assert
            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<ArticleType>) result).errors()).singleElement()
                    .satisfies(e -> assertThat(e.code()).isEqualTo("ARTICLE_TYPE_INVALID"));
        }

        @Test
        @DisplayName("小文字は不正エラーになる（列挙子名は大文字）")
        void lowercaseShouldFailAsInvalid() {
            // Act
            Result<ArticleType> result = ArticleType.fromInput("album");

            // Assert
            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<ArticleType>) result).errors()).singleElement()
                    .satisfies(e -> assertThat(e.code()).isEqualTo("ARTICLE_TYPE_INVALID"));
        }
    }
}
