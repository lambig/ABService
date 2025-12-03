package com.abservice.domain.model.vo.article;

import static org.junit.jupiter.api.Assertions.*;

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
            var content = MarkupContent.plainText("Hello, World!");

            // Assert
            assertNotNull(content);
            assertEquals("Hello, World!", content.content());
            assertEquals(MarkupFormat.PLAIN_TEXT, content.format());
        }

        @Test
        @DisplayName("Markdownを生成できること")
        void createMarkdownShouldSucceed() {
            // Arrange & Act
            var content = MarkupContent.markdown("# Title\n\nThis is **bold**.");

            // Assert
            assertNotNull(content);
            assertEquals("# Title\n\nThis is **bold**.", content.content());
            assertEquals(MarkupFormat.MARKDOWN, content.format());
        }

        @Test
        @DisplayName("HTMLを生成できること")
        void createHtmlShouldSucceed() {
            // Arrange & Act
            var content = MarkupContent.html("<h1>Title</h1><p>Paragraph</p>");

            // Assert
            assertNotNull(content);
            assertEquals("<h1>Title</h1><p>Paragraph</p>", content.content());
            assertEquals(MarkupFormat.HTML, content.format());
        }

        @Test
        @DisplayName("nullコンテンツでも生成できること")
        void createWithNullContentShouldSucceed() {
            // Arrange & Act
            var content = MarkupContent.markdown(null);

            // Assert
            assertNotNull(content);
            assertNull(content.content());
            assertEquals(MarkupFormat.MARKDOWN, content.format());
        }

        @Test
        @DisplayName("nullフォーマットでは例外が発生すること")
        void createWithNullFormatShouldThrowException() {
            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                new MarkupContent("content", null);
            });
            assertEquals("Markup format cannot be null", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("空判定テスト")
    class IsEmptyTest {

        @Test
        @DisplayName("nullコンテンツは空と判定されること")
        void nullContentShouldBeEmpty() {
            // Arrange
            var content = MarkupContent.plainText(null);

            // Act & Assert
            assertTrue(content.isEmpty());
        }

        @Test
        @DisplayName("空文字列コンテンツは空と判定されること")
        void emptyStringContentShouldBeEmpty() {
            // Arrange
            var content = MarkupContent.plainText("");

            // Act & Assert
            assertTrue(content.isEmpty());
        }

        @Test
        @DisplayName("空白のみのコンテンツは空と判定されること")
        void blankContentShouldBeEmpty() {
            // Arrange
            var content = MarkupContent.plainText("   ");

            // Act & Assert
            assertTrue(content.isEmpty());
        }

        @Test
        @DisplayName("テキストを含むコンテンツは空でないと判定されること")
        void nonEmptyContentShouldNotBeEmpty() {
            // Arrange
            var content = MarkupContent.markdown("Content");

            // Act & Assert
            assertFalse(content.isEmpty());
        }
    }

    @Nested
    @DisplayName("長さ取得テスト")
    class LengthTest {

        @Test
        @DisplayName("nullコンテンツの長さは0であること")
        void nullContentLengthShouldBeZero() {
            // Arrange
            var content = MarkupContent.plainText(null);

            // Act & Assert
            assertEquals(0, content.length());
        }

        @Test
        @DisplayName("コンテンツの文字数を取得できること")
        void shouldReturnCorrectLength() {
            // Arrange
            var content = MarkupContent.markdown("Hello");

            // Act & Assert
            assertEquals(5, content.length());
        }
    }

    @Nested
    @DisplayName("等価性テスト")
    class EquivalenceTest {

        @Test
        @DisplayName("同じ内容と形式は等価であること")
        void sameContentAndFormatShouldBeEquivalent() {
            // Arrange
            var content1 = MarkupContent.markdown("Test");
            var content2 = MarkupContent.markdown("Test");

            // Act & Assert
            assertTrue(content1.equivalentTo(content2));
            assertEquals(content1, content2);
        }

        @Test
        @DisplayName("異なる内容は等価でないこと")
        void differentContentShouldNotBeEquivalent() {
            // Arrange
            var content1 = MarkupContent.markdown("Test1");
            var content2 = MarkupContent.markdown("Test2");

            // Act & Assert
            assertFalse(content1.equivalentTo(content2));
            assertNotEquals(content1, content2);
        }

        @Test
        @DisplayName("異なる形式は等価でないこと")
        void differentFormatShouldNotBeEquivalent() {
            // Arrange
            var content1 = MarkupContent.markdown("Test");
            var content2 = MarkupContent.html("Test");

            // Act & Assert
            assertFalse(content1.equivalentTo(content2));
            assertNotEquals(content1, content2);
        }

        @Test
        @DisplayName("nullとの比較は等価でないこと")
        void nullShouldNotBeEquivalent() {
            // Arrange
            var content = MarkupContent.plainText("Test");

            // Act & Assert
            assertFalse(content.equivalentTo(null));
        }

        @Test
        @DisplayName("両方nullのコンテンツは等価であること")
        void bothNullContentShouldBeEquivalent() {
            // Arrange
            var content1 = MarkupContent.markdown(null);
            var content2 = MarkupContent.markdown(null);

            // Act & Assert
            assertTrue(content1.equivalentTo(content2));
            assertEquals(content1, content2);
        }
    }
}
