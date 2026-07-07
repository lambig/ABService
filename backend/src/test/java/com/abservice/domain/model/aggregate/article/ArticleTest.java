package com.abservice.domain.model.aggregate.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
            assertThat(article).isNotNull();
            assertThat(article.id()).isNotNull();
            assertThat(article.articleType()).isEqualTo(articleType);
            assertThat(article.albumId()).isNull();
            assertThat(article.title()).isEqualTo(title);
            assertThat(article.body()).isEqualTo(body);
            assertThat(article.introShort()).isNull();
            assertThat(article.publishedAt()).isNull();
            assertThat(article.updatedAtBusiness()).isNull();
            assertThat(article.publicFlag()).isFalse();
            assertThat(article.getTags().isEmpty()).isTrue();
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
            assertThat(article.articleType()).isEqualTo(ArticleType.ALBUM);
            assertThat(article.albumId()).isEqualTo(albumId);
            assertThat(article.introShort()).isEqualTo(introShort);
        }

        @Test
        @DisplayName("記事種別がnullの場合は例外が発生すること")
        void createWithNullArticleTypeShouldThrowException() {
            // Act & Assert
            assertThatThrownBy(() -> {
                Article.create(null, null, "Title", MarkupContent.plainText("Body"), null);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Article type cannot be null");
        }

        @Test
        @DisplayName("タイトルがnullの場合は例外が発生すること")
        void createWithNullTitleShouldThrowException() {
            // Act & Assert
            assertThatThrownBy(() -> {
                Article.create(ArticleType.NOTE, null, null, MarkupContent.plainText("Body"), null);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Article title cannot be null or blank");
        }

        @Test
        @DisplayName("タイトルが空文字の場合は例外が発生すること")
        void createWithBlankTitleShouldThrowException() {
            // Act & Assert
            assertThatThrownBy(() -> {
                Article.create(ArticleType.NOTE, null, "   ", MarkupContent.plainText("Body"), null);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Article title cannot be null or blank");
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
            assertThat(updated.title()).isEqualTo(newTitle);
            assertThat(updated.updatedAtBusiness()).isNotNull();
        }

        @Test
        @DisplayName("nullのタイトルに変更しようとすると例外が発生すること")
        void changeTitleWithNullShouldThrowException() {
            // Arrange
            var article = createTestArticle();
            var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act & Assert
            assertThatThrownBy(() -> {
                article.changeTitle(null, currentDateTime);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Article title cannot be null or blank");
        }

        @Test
        @DisplayName("空文字のタイトルに変更しようとすると例外が発生すること")
        void changeTitleWithBlankShouldThrowException() {
            // Arrange
            var article = createTestArticle();
            var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act & Assert
            assertThatThrownBy(() -> {
                article.changeTitle("  ", currentDateTime);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Article title cannot be null or blank");
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
            assertThat(updated.body()).isEqualTo(newBody);
            assertThat(updated.updatedAtBusiness()).isNotNull();
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
            assertThat(updated.body()).isNull();
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
            assertThat(article.isPublic()).isFalse();

            // Act
            var published = article.publish(currentDateTime);

            // Assert
            assertThat(published.isPublic()).isTrue();
            assertThat(published.publicFlag()).isTrue();
            assertThat(published.publishedAt()).isNotNull();
            assertThat(published.updatedAtBusiness()).isNotNull();
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
            assertThat(republished.publishedAt()).isEqualTo(originalPublishedAt);
        }

        @Test
        @DisplayName("記事を非公開化できること")
        void unpublishShouldUnsetPublicFlag() {
            // Arrange
            var currentDateTime = BusinessDateTime.of(Instant.now());
            var article = createTestArticle().publish(currentDateTime);
            assertThat(article.isPublic()).isTrue();

            // Act
            var unpublished = article.unpublish(currentDateTime);

            // Assert
            assertThat(unpublished.isPublic()).isFalse();
            assertThat(unpublished.publicFlag()).isFalse();
            assertThat(unpublished.updatedAtBusiness()).isNotNull();
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
            assertThat(updated.albumId()).isEqualTo(albumId);
            assertThat(updated.updatedAtBusiness()).isNotNull();
        }

        @Test
        @DisplayName("アルバム記事以外にアルバムIDを設定しようとすると例外が発生すること")
        void setAlbumIdForNonAlbumArticleShouldThrowException() {
            // Arrange
            var article = Article.create(ArticleType.NOTE, null, "Blog Post", MarkupContent.plainText("Body"), null);
            var albumId = Album.Id.generate();
            var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act & Assert
            assertThatThrownBy(() -> {
                article.setAlbumId(albumId, currentDateTime);
            }).isInstanceOf(IllegalStateException.class).hasMessage("Cannot set album ID for non-ALBUM article type");
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
            assertThat(updated.articleType()).isEqualTo(ArticleType.NEWS);
            assertThat(updated.updatedAtBusiness()).isNotNull();
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
            assertThat(updated.articleType()).isEqualTo(ArticleType.NOTE);
            assertThat(updated.albumId()).isNull();
        }

        @Test
        @DisplayName("nullの記事種別に変更しようとすると例外が発生すること")
        void changeArticleTypeWithNullShouldThrowException() {
            // Arrange
            var article = createTestArticle();
            var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act & Assert
            assertThatThrownBy(() -> {
                article.changeArticleType(null, currentDateTime);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Article type cannot be null");
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
            assertThat(updated.getTags().size()).isEqualTo(1);
            assertThat(updated.getTags().contains(tag)).isTrue();
            assertThat(updated.updatedAtBusiness()).isNotNull();
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
            assertThat(updated.getTags().size()).isEqualTo(3);
            assertThat(updated.getTags()).isEqualTo(List.of(tag1, tag2, tag3));
        }

        @Test
        @DisplayName("nullのタグを追加しようとすると例外が発生すること")
        void addTagWithNullShouldThrowException() {
            // Arrange
            var article = createTestArticle();
            var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act & Assert
            assertThatThrownBy(() -> {
                article.addTag(null, currentDateTime);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Tag cannot be null");
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
            assertThatThrownBy(() -> {
                finalArticle.addTag(tag2, currentDateTime);
            }).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already exists");
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
            assertThat(updated.getTags().size()).isEqualTo(0);
            assertThat(updated.getTags().contains(tag)).isFalse();
            assertThat(updated.updatedAtBusiness()).isNotNull();
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
            assertThat(updated.getTags().size()).isEqualTo(2);
            assertThat(updated.getTags().contains(tag1)).isTrue();
            assertThat(updated.getTags().contains(tag2)).isFalse();
            assertThat(updated.getTags().contains(tag3)).isTrue();
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
            assertThat(updated.getTags().size()).isEqualTo(1); // タグは削除されていない
        }

        @Test
        @DisplayName("nullのIDでタグを削除しようとすると例外が発生すること")
        void removeTagWithNullIdShouldThrowException() {
            // Arrange
            var article = createTestArticle();
            var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act & Assert
            assertThatThrownBy(() -> {
                article.removeTag(null, currentDateTime);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Tag ID cannot be null");
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
            assertThat(id).isNotNull();
            assertThat(id.value()).isNotNull();
            assertThat(id.value().isBlank()).isFalse();
        }

        @Test
        @DisplayName("文字列からIDを生成できること")
        void ofWithValidUuidShouldSucceed() {
            // Arrange
            var validUuid = Article.Id.generate().value();

            // Act
            var id = Article.Id.of(validUuid);

            // Assert
            assertThat(id.value()).isEqualTo(validUuid);
        }

        @Test
        @DisplayName("空文字列からIDを生成しようとすると例外が発生すること")
        void ofWithBlankStringShouldThrowException() {
            // Act & Assert
            assertThatThrownBy(() -> {
                Article.Id.of("");
            }).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("不正なUUID形式の文字列からIDを生成しようとすると例外が発生すること")
        void ofWithInvalidUuidShouldThrowException() {
            // Act & Assert
            assertThatThrownBy(() -> {
                Article.Id.of("invalid-uuid");
            }).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("同じ値のIDは等しいこと")
        void equalsWithSameValueShouldBeEqual() {
            // Arrange
            var value = Article.Id.generate().value();
            var id1 = Article.Id.of(value);
            var id2 = Article.Id.of(value);

            // Act & Assert
            assertThat(id2).isEqualTo(id1);
            assertThat(id2.hashCode()).isEqualTo(id1.hashCode());
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
