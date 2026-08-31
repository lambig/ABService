package com.abservice.domain.model.vo.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.lib.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MarkupContent値オブジェクトのテスト")
class MarkupContentTest {

    @Nested
    @DisplayName("生成テスト")
    class CreateTest {

        @Test
        @DisplayName("プレーンテキストを生成できること")
        void createPlainTextShouldSucceed() {
            // Arrange & Act
            final var content = MarkupContent.plainText("Hello, World!");

            // Assert
            assertThat(content).isNotNull();
            assertThat(content.content()).isEqualTo("Hello, World!");
            assertThat(content.format()).isEqualTo(MarkupFormat.PLAIN_TEXT);
        }

        @Test
        @DisplayName("Markdownを生成できること")
        void createMarkdownShouldSucceed() {
            // Arrange & Act
            final var content = MarkupContent.markdown("# Title\n\nThis is **bold**.");

            // Assert
            assertThat(content).isNotNull();
            assertThat(content.content()).isEqualTo("# Title\n\nThis is **bold**.");
            assertThat(content.format()).isEqualTo(MarkupFormat.MARKDOWN);
        }

        @Test
        @DisplayName("nullコンテンツは空文字列として生成されること")
        void createWithNullContentShouldConvertToEmpty() {
            // Arrange & Act
            final var content = MarkupContent.markdown(null);

            // Assert
            assertThat(content).isNotNull();
            assertThat(content.content()).isEqualTo("");
            assertThat(content.format()).isEqualTo(MarkupFormat.MARKDOWN);
        }

        @Test
        @DisplayName("nullフォーマットでは例外が発生すること")
        void createWithNullFormatShouldThrowException() {
            // Act & Assert
            assertThatThrownBy(() -> new MarkupContent("content", null)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Markup format cannot be null");
        }
    }

    @Nested
    @DisplayName("空判定テスト")
    class IsEmptyTest {

        @Test
        @DisplayName("plainTextでnullコンテンツを渡すと例外が発生すること")
        void plainTextWithNullShouldThrowException() {
            // Act & Assert
            assertThatThrownBy(() -> MarkupContent.plainText(null)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Content cannot be null");
        }

        @Test
        @DisplayName("空文字列コンテンツは空と判定されること")
        void emptyStringContentShouldBeEmpty() {
            // Arrange
            final var content = MarkupContent.plainText("");

            // Act & Assert
            assertThat(content.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("空白のみのコンテンツは空と判定されること")
        void blankContentShouldBeEmpty() {
            // Arrange
            final var content = MarkupContent.plainText("   ");

            // Act & Assert
            assertThat(content.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("テキストを含むコンテンツは空でないと判定されること")
        void nonEmptyContentShouldNotBeEmpty() {
            // Arrange
            final var content = MarkupContent.markdown("Content");

            // Act & Assert
            assertThat(content.isEmpty()).isFalse();
        }
    }

    @Nested
    @DisplayName("長さ取得テスト")
    class LengthTest {

        @Test
        @DisplayName("空文字列コンテンツの長さは0であること")
        void emptyContentLengthShouldBeZero() {
            // Arrange
            final var content = MarkupContent.markdown("");

            // Act & Assert
            assertThat(content.length()).isEqualTo(0);
        }

        @Test
        @DisplayName("コンテンツの文字数を取得できること")
        void shouldReturnCorrectLength() {
            // Arrange
            final var content = MarkupContent.markdown("Hello");

            // Act & Assert
            assertThat(content.length()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("等価性テスト")
    class EquivalenceTest {

        @Test
        @DisplayName("同じ内容と形式は等価であること")
        void sameContentAndFormatShouldBeEquivalent() {
            // Arrange
            final var content1 = MarkupContent.markdown("Test");
            final var content2 = MarkupContent.markdown("Test");

            // Act & Assert
            assertThat(content1.equivalentTo(content2)).isTrue();
            assertThat(content2).isEqualTo(content1);
        }

        @Test
        @DisplayName("異なる内容は等価でないこと")
        void differentContentShouldNotBeEquivalent() {
            // Arrange
            final var content1 = MarkupContent.markdown("Test1");
            final var content2 = MarkupContent.markdown("Test2");

            // Act & Assert
            assertThat(content1.equivalentTo(content2)).isFalse();
            assertThat(content2).isNotEqualTo(content1);
        }

        @Test
        @DisplayName("異なる形式は等価でないこと")
        void differentFormatShouldNotBeEquivalent() {
            // Arrange
            final var content1 = MarkupContent.markdown("Test");
            final var content2 = MarkupContent.plainText("Test");

            // Act & Assert
            assertThat(content1.equivalentTo(content2)).isFalse();
            assertThat(content2).isNotEqualTo(content1);
        }

        @Test
        @DisplayName("nullとの比較は等価でないこと")
        void nullShouldNotBeEquivalent() {
            // Arrange
            final var content = MarkupContent.plainText("Test");

            // Act & Assert
            assertThat(content.equivalentTo(null)).isFalse();
        }

        @Test
        @DisplayName("両方空文字列のコンテンツは等価であること")
        void bothEmptyContentShouldBeEquivalent() {
            // Arrange
            final var content1 = MarkupContent.markdown("");
            final var content2 = MarkupContent.markdown("");

            // Act & Assert
            assertThat(content1.equivalentTo(content2)).isTrue();
            assertThat(content2).isEqualTo(content1);
        }
    }

    @Nested
    @DisplayName("fromInput（外部入力からの生成）")
    class FromInputTest {

        @Test
        @DisplayName("有効な形式とコンテンツで成功する")
        void validInputShouldSucceed() {
            // Act
            final Result<MarkupContent> result = MarkupContent.fromInput("# Title", "MARKDOWN");

            // Assert
            final var content = result.resolve();
            assertThat(content.content()).isEqualTo("# Title");
            assertThat(content.format()).isEqualTo(MarkupFormat.MARKDOWN);
        }

        @Test
        @DisplayName("形式の前後空白を許容する")
        void surroundingWhitespaceInFormatIsTrimmed() {
            // Act
            final Result<MarkupContent> result = MarkupContent.fromInput("x", "  MARKDOWN  ");

            // Assert
            assertThat(result.resolve().format()).isEqualTo(MarkupFormat.MARKDOWN);
        }

        @Test
        @DisplayName("HTMLは形式として受け付けない")
        void htmlFormatShouldFailAsInvalid() {
            // Act
            final Result<MarkupContent> result = MarkupContent.fromInput("<p>x</p>", "HTML");

            // Assert
            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<MarkupContent>) result).errors()).singleElement()
                    .satisfies(e -> assertThat(e.code()).isEqualTo("MARKUP_FORMAT_INVALID"));
        }

        @Test
        @DisplayName("nullコンテンツは空文字列として成功する")
        void nullContentShouldBecomeEmpty() {
            // Act
            final Result<MarkupContent> result = MarkupContent.fromInput(null, "PLAIN_TEXT");

            // Assert
            assertThat(result.resolve().content()).isEmpty();
        }

        @Test
        @DisplayName("形式がnullは必須エラーになる")
        void nullFormatShouldFailAsRequired() {
            // Act
            final Result<MarkupContent> result = MarkupContent.fromInput("x", null);

            // Assert
            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<MarkupContent>) result).errors()).singleElement().satisfies(e -> {
                assertThat(e.field()).isEqualTo("format");
                assertThat(e.code()).isEqualTo("MARKUP_FORMAT_REQUIRED");
            });
        }

        @Test
        @DisplayName("未知の形式は不正エラーになる")
        void unknownFormatShouldFailAsInvalid() {
            // Act
            final Result<MarkupContent> result = MarkupContent.fromInput("x", "XML");

            // Assert
            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<MarkupContent>) result).errors()).singleElement()
                    .satisfies(e -> assertThat(e.code()).isEqualTo("MARKUP_FORMAT_INVALID"));
        }
    }
}
