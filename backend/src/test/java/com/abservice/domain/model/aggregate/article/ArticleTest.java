package com.abservice.domain.model.aggregate.article;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.entity.article.ArticleTag;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.model.vo.article.MarkupContent;
import com.abservice.domain.model.vo.common.BusinessDateTime;

@DisplayName("Article集約のテスト")
class ArticleTest {

    @Nested
    @DisplayName("生成テスト")
    class CreateTest {

        @Test
        @DisplayName("正常な値で生成できること")
        void createWithValidValuesShouldSucceed() {
            // Arrange
            var articleType = ArticleType.NOTE;
            var title = "Test Article";
            var body = MarkupContent.markdown("This is a test article body.");

            // Act
            var article = Article.create(articleType, null, title, body, null);

            // Assert
            assertNotNull(article);
            assertNotNull(article.id());
            assertEquals(articleType, article.articleType());
            assertNull(article.albumId());
            assertEquals(title, article.title());
            assertEquals(body, article.body());
            assertNull(article.introShort());
            assertNull(article.publishedAt());
            assertNull(article.updatedAtBusiness());
            assertFalse(article.publicFlag());
            assertTrue(article.getTags().isEmpty());
        }

        @Test
        @DisplayName("アルバム記事として生成できること")
        void createAsAlbumArticleShouldSucceed() {
            // Arrange
            var articleType = ArticleType.ALBUM;
            var albumId = Album.Id.generate();
            var title = "Album Review";
            var body = MarkupContent.markdown("This is an album review.");
            var introShort = "Short intro";

            // Act
            var article = Article.create(articleType, albumId, title, body, introShort);

            // Assert
            assertEquals(ArticleType.ALBUM, article.articleType());
            assertEquals(albumId, article.albumId());
            assertEquals(introShort, article.introShort());
        }

        @Test
        @DisplayName("記事種別がnullの場合は例外が発生すること")
        void createWithNullArticleTypeShouldThrowException() {
            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                Article.create(null, null, "Title", MarkupContent.plainText("Body"), null);
            });
            assertEquals("Article type cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("タイトルがnullの場合は例外が発生すること")
        void createWithNullTitleShouldThrowException() {
            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                Article.create(ArticleType.NOTE, null, null, MarkupContent.plainText("Body"), null);
            });
            assertEquals("Article title cannot be null or blank", exception.getMessage());
        }

        @Test
        @DisplayName("タイトルが空文字の場合は例外が発生すること")
        void createWithBlankTitleShouldThrowException() {
            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                Article.create(ArticleType.NOTE, null, "   ", MarkupContent.plainText("Body"), null);
            });
            assertEquals("Article title cannot be null or blank", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("タイトル変更テスト")
    class ChangeTitleTest {

        @Test
        @DisplayName("タイトルを変更できること")
        void changeTitleWithValidTitleShouldSucceed() {
            // Arrange
            var article = createTestArticle();
            var newTitle = "Updated Title";
            var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act
            var updated = article.changeTitle(newTitle, currentDateTime);

            // Assert
            assertEquals(newTitle, updated.title());
            assertNotNull(updated.updatedAtBusiness());
        }

        @Test
        @DisplayName("nullのタイトルに変更しようとすると例外が発生すること")
        void changeTitleWithNullShouldThrowException() {
            // Arrange
            var article = createTestArticle();
            var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                article.changeTitle(null, currentDateTime);
            });
            assertEquals("Article title cannot be null or blank", exception.getMessage());
        }

        @Test
        @DisplayName("空文字のタイトルに変更しようとすると例外が発生すること")
        void changeTitleWithBlankShouldThrowException() {
            // Arrange
            var article = createTestArticle();
            var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                article.changeTitle("  ", currentDateTime);
            });
            assertEquals("Article title cannot be null or blank", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("本文変更テスト")
    class ChangeBodyTest {

        @Test
        @DisplayName("本文を変更できること")
        void changeBodyWithValidBodyShouldSucceed() {
            // Arrange
            var article = createTestArticle();
            var newBody = MarkupContent.markdown("Updated body content");
            var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act
            var updated = article.changeBody(newBody, currentDateTime);

            // Assert
            assertEquals(newBody, updated.body());
            assertNotNull(updated.updatedAtBusiness());
        }

        @Test
        @DisplayName("本文をnullに変更できること")
        void changeBodyWithNullShouldSucceed() {
            // Arrange
            var article = createTestArticle();
            var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act
            var updated = article.changeBody(null, currentDateTime);

            // Assert
            assertNull(updated.body());
        }
    }

    @Nested
    @DisplayName("公開・非公開テスト")
    class PublishTest {

        @Test
        @DisplayName("記事を公開できること")
        void publishShouldSetPublicFlag() {
            // Arrange
            var article = createTestArticle();
            var currentDateTime = BusinessDateTime.of(Instant.now());
            assertFalse(article.isPublic());

            // Act
            var published = article.publish(currentDateTime);

            // Assert
            assertTrue(published.isPublic());
            assertTrue(published.publicFlag());
            assertNotNull(published.publishedAt());
            assertNotNull(published.updatedAtBusiness());
        }

        @Test
        @DisplayName("既に公開済みの記事を再度公開してもpublishedAtは変わらないこと")
        void publishAlreadyPublishedShouldKeepOriginalPublishedAt() {
            // Arrange
            var article = createTestArticle();
            var currentDateTime = BusinessDateTime.of(Instant.now());
            var firstPublished = article.publish(currentDateTime);
            var originalPublishedAt = firstPublished.publishedAt();

            // Act
            var republished = firstPublished.publish(currentDateTime);

            // Assert
            assertEquals(originalPublishedAt, republished.publishedAt());
        }

        @Test
        @DisplayName("記事を非公開化できること")
        void unpublishShouldUnsetPublicFlag() {
            // Arrange
            var currentDateTime = BusinessDateTime.of(Instant.now());
            var article = createTestArticle().publish(currentDateTime);
            assertTrue(article.isPublic());

            // Act
            var unpublished = article.unpublish(currentDateTime);

            // Assert
            assertFalse(unpublished.isPublic());
            assertFalse(unpublished.publicFlag());
            assertNotNull(unpublished.updatedAtBusiness());
        }
    }

    @Nested
    @DisplayName("アルバムID設定テスト")
    class SetAlbumIdTest {

        @Test
        @DisplayName("アルバム記事にアルバムIDを設定できること")
        void setAlbumIdForAlbumArticleShouldSucceed() {
            // Arrange
            var article = Article.create(ArticleType.ALBUM, null, "Album Article", MarkupContent.plainText("Body"),
                    null);
            var albumId = Album.Id.generate();
            var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act
            var updated = article.setAlbumId(albumId, currentDateTime);

            // Assert
            assertEquals(albumId, updated.albumId());
            assertNotNull(updated.updatedAtBusiness());
        }

        @Test
        @DisplayName("アルバム記事以外にアルバムIDを設定しようとすると例外が発生すること")
        void setAlbumIdForNonAlbumArticleShouldThrowException() {
            // Arrange
            var article = Article.create(ArticleType.NOTE, null, "Blog Post", MarkupContent.plainText("Body"), null);
            var albumId = Album.Id.generate();
            var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act & Assert
            var exception = assertThrows(IllegalStateException.class, () -> {
                article.setAlbumId(albumId, currentDateTime);
            });
            assertEquals("Cannot set album ID for non-ALBUM article type", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("記事種別変更テスト")
    class ChangeArticleTypeTest {

        @Test
        @DisplayName("記事種別を変更できること")
        void changeArticleTypeWithValidTypeShouldSucceed() {
            // Arrange
            var article = Article.create(ArticleType.NOTE, null, "Title", MarkupContent.plainText("Body"), null);
            var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act
            var updated = article.changeArticleType(ArticleType.NEWS, currentDateTime);

            // Assert
            assertEquals(ArticleType.NEWS, updated.articleType());
            assertNotNull(updated.updatedAtBusiness());
        }

        @Test
        @DisplayName("アルバム記事から他の種別に変更するとアルバムIDがクリアされること")
        void changeArticleTypeFromAlbumToOtherShouldClearAlbumId() {
            // Arrange
            var albumId = Album.Id.generate();
            var article = Article.create(ArticleType.ALBUM, albumId, "Album Article", MarkupContent.plainText("Body"),
                    null);
            var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act
            var updated = article.changeArticleType(ArticleType.NOTE, currentDateTime);

            // Assert
            assertEquals(ArticleType.NOTE, updated.articleType());
            assertNull(updated.albumId());
        }

        @Test
        @DisplayName("nullの記事種別に変更しようとすると例外が発生すること")
        void changeArticleTypeWithNullShouldThrowException() {
            // Arrange
            var article = createTestArticle();
            var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                article.changeArticleType(null, currentDateTime);
            });
            assertEquals("Article type cannot be null", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("タグ追加テスト")
    class AddTagTest {

        @Test
        @DisplayName("タグを追加できること")
        void addTagWithValidTagShouldSucceed() {
            // Arrange
            var article = createTestArticle();
            var tag = createTestTag("TestTag");
            var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act
            var updated = article.addTag(tag, currentDateTime);

            // Assert
            assertEquals(1, updated.getTags().size());
            assertTrue(updated.getTags().contains(tag));
            assertNotNull(updated.updatedAtBusiness());
        }

        @Test
        @DisplayName("複数のタグを追加できること")
        void addTagWithMultipleTagsShouldSucceed() {
            // Arrange
            var article = createTestArticle();
            var tag1 = createTestTag("Tag1");
            var tag2 = createTestTag("Tag2");
            var tag3 = createTestTag("Tag3");
            var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act
            var updated = article.addTag(tag1, currentDateTime).addTag(tag2, currentDateTime).addTag(tag3,
                    currentDateTime);

            // Assert
            assertEquals(3, updated.getTags().size());
            assertEquals(List.of(tag1, tag2, tag3), updated.getTags());
        }

        @Test
        @DisplayName("nullのタグを追加しようとすると例外が発生すること")
        void addTagWithNullShouldThrowException() {
            // Arrange
            var article = createTestArticle();
            var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                article.addTag(null, currentDateTime);
            });
            assertEquals("Tag cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("重複するIDのタグを追加しようとすると例外が発生すること")
        void addTagWithDuplicateIdShouldThrowException() {
            // Arrange
            var article = createTestArticle();
            var tag1 = createTestTag("Tag1");
            var tag2 = ArticleTag.reconstruct(tag1.id(), "Tag2"); // 同じIDで異なる名前
            var currentDateTime = BusinessDateTime.of(Instant.now());
            article = article.addTag(tag1, currentDateTime);

            // Act & Assert
            var finalArticle = article;
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                finalArticle.addTag(tag2, currentDateTime);
            });
            assertTrue(exception.getMessage().contains("already exists"));
        }
    }

    @Nested
    @DisplayName("タグ削除テスト")
    class RemoveTagTest {

        @Test
        @DisplayName("タグを削除できること")
        void removeTagWithExistingTagShouldSucceed() {
            // Arrange
            var article = createTestArticle();
            var tag = createTestTag("TagToRemove");
            var currentDateTime = BusinessDateTime.of(Instant.now());
            article = article.addTag(tag, currentDateTime);

            // Act
            var updated = article.removeTag(tag.id(), currentDateTime);

            // Assert
            assertEquals(0, updated.getTags().size());
            assertFalse(updated.getTags().contains(tag));
            assertNotNull(updated.updatedAtBusiness());
        }

        @Test
        @DisplayName("複数のタグから特定のタグを削除できること")
        void removeTagFromMultipleTagsShouldSucceed() {
            // Arrange
            var article = createTestArticle();
            var tag1 = createTestTag("Tag1");
            var tag2 = createTestTag("Tag2");
            var tag3 = createTestTag("Tag3");
            var currentDateTime = BusinessDateTime.of(Instant.now());
            article = article.addTag(tag1, currentDateTime).addTag(tag2, currentDateTime).addTag(tag3, currentDateTime);

            // Act
            var updated = article.removeTag(tag2.id(), currentDateTime);

            // Assert
            assertEquals(2, updated.getTags().size());
            assertTrue(updated.getTags().contains(tag1));
            assertFalse(updated.getTags().contains(tag2));
            assertTrue(updated.getTags().contains(tag3));
        }

        @Test
        @DisplayName("存在しないタグを削除してもエラーにならないこと")
        void removeTagWithNonExistentTagShouldSucceed() {
            // Arrange
            var article = createTestArticle();
            var tag = createTestTag("Tag");
            var currentDateTime = BusinessDateTime.of(Instant.now());
            article = article.addTag(tag, currentDateTime);
            var nonExistentId = ArticleTag.Id.generate();

            // Act
            var updated = article.removeTag(nonExistentId, currentDateTime);

            // Assert
            assertEquals(1, updated.getTags().size()); // タグは削除されていない
        }

        @Test
        @DisplayName("nullのIDでタグを削除しようとすると例外が発生すること")
        void removeTagWithNullIdShouldThrowException() {
            // Arrange
            var article = createTestArticle();
            var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                article.removeTag(null, currentDateTime);
            });
            assertEquals("Tag ID cannot be null", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Article.Idのテスト")
    class IdTest {

        @Test
        @DisplayName("IDを生成できること")
        void generateShouldCreateValidId() {
            // Act
            var id = Article.Id.generate();

            // Assert
            assertNotNull(id);
            assertNotNull(id.value());
            assertFalse(id.value().isBlank());
        }

        @Test
        @DisplayName("文字列からIDを生成できること")
        void ofWithValidUuidShouldSucceed() {
            // Arrange
            var validUuid = Article.Id.generate().value();

            // Act
            var id = Article.Id.of(validUuid);

            // Assert
            assertEquals(validUuid, id.value());
        }

        @Test
        @DisplayName("空文字列からIDを生成しようとすると例外が発生すること")
        void ofWithBlankStringShouldThrowException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                Article.Id.of("");
            });
        }

        @Test
        @DisplayName("不正なUUID形式の文字列からIDを生成しようとすると例外が発生すること")
        void ofWithInvalidUuidShouldThrowException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                Article.Id.of("invalid-uuid");
            });
        }

        @Test
        @DisplayName("同じ値のIDは等しいこと")
        void equalsWithSameValueShouldBeEqual() {
            // Arrange
            var value = Article.Id.generate().value();
            var id1 = Article.Id.of(value);
            var id2 = Article.Id.of(value);

            // Act & Assert
            assertEquals(id1, id2);
            assertEquals(id1.hashCode(), id2.hashCode());
        }
    }

    // テストヘルパーメソッド

    private Article createTestArticle() {
        return Article.create(ArticleType.NOTE, null, "Test Article",
                MarkupContent.markdown("Test article body content"), null);
    }

    private ArticleTag createTestTag(String name) {
        return ArticleTag.create(name);
    }
}
