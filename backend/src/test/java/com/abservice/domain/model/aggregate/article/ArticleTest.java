package com.abservice.domain.model.aggregate.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.entity.article.ArticleTag;
import com.abservice.domain.model.vo.article.ArticleTitle;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.domain.model.vo.common.MarkupContent;
import com.abservice.lib.Result;

@DisplayName("Article集約のテスト")
class ArticleTest {

    @Nested
    @DisplayName("生成テスト")
    class CreateTest {

        @Test
        @DisplayName("正常な値で生成できること")
        void createWithValidValuesShouldSucceed() {
            // Arrange
            final var articleType = ArticleType.NOTE;
            final var title = ArticleTitle.of("Test Article");
            final var body = MarkupContent.markdown("This is a test article body.");

            // Act
            final var article = Article.create(
                    articleType,
                    null,
                    title,
                    body,
                    null,
                    BusinessDateTime.of(Instant.now()));

            // Assert
            assertThat(article).isNotNull();
            assertThat(article.id()).isNotNull();
            assertThat(article.articleType()).isEqualTo(articleType);
            assertThat(article).isInstanceOf(NoteArticle.class);
            assertThat(AlbumArticle.from(article)).isEmpty();
            assertThat(article.title()).isEqualTo(title);
            assertThat(article.body()).isEqualTo(body);
            assertThat(article.introShort()).isNull();
            assertThat(article.publishedAt()).isNull();
            // 記事を書き起こすことも業務上の更新の一つのため、作成時点で値を持つ
            assertThat(article.updatedAtBusiness()).isNotNull();
            assertThat(article.publicFlag()).isFalse();
            assertThat(article.getTags().isEmpty()).isTrue();
        }

        @Test
        @DisplayName("アルバム記事として生成できること")
        void createAsAlbumArticleShouldSucceed() {
            // Arrange
            final var articleType = ArticleType.ALBUM;
            final var albumId = Album.Id.generate();
            final var title = ArticleTitle.of("Album Review");
            final var body = MarkupContent.markdown("This is an album review.");
            final var introShort = "Short intro";

            // Act
            final var article = Article.create(
                    articleType,
                    albumId,
                    title,
                    body,
                    introShort,
                    BusinessDateTime.of(Instant.now()));

            // Assert
            assertThat(article.articleType()).isEqualTo(ArticleType.ALBUM);
            assertThat(
                    AlbumArticle.from(article)
                            .orElseThrow()
                            .albumReference()
                            .activeAlbumId())
                    .contains(albumId);
            assertThat(article.introShort()).isEqualTo(introShort);
        }

        @Test
        @DisplayName("記事種別がnullの場合は例外が発生すること")
        void createWithNullArticleTypeShouldThrowException() {
            // Act & Assert
            assertThatThrownBy(() -> {
                Article.create(
                        null,
                        null,
                        ArticleTitle.of("Title"),
                        MarkupContent.plainText("Body"),
                        null,
                        BusinessDateTime.of(Instant.now()));
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Article type cannot be null");
        }

        @Test
        @DisplayName("タイトルがnullの場合は例外が発生すること")
        void createWithNullTitleShouldThrowException() {
            // Act & Assert
            assertThatThrownBy(() -> {
                Article.create(
                        ArticleType.NOTE,
                        null,
                        null,
                        MarkupContent.plainText("Body"),
                        null,
                        BusinessDateTime.of(Instant.now()));
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Article title cannot be null");
        }
    }

    @Nested
    @DisplayName("タイトル変更テスト")
    class ChangeTitleTest {

        @Test
        @DisplayName("タイトルを変更できること")
        void changeTitleWithValidTitleShouldSucceed() {
            // Arrange
            final var article = createTestArticle();
            final var newTitle = ArticleTitle.of("Updated Title");
            final var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act
            final var updated = article.changeTitle(newTitle, currentDateTime);

            // Assert
            assertThat(updated.title()).isEqualTo(newTitle);
            assertThat(updated.updatedAtBusiness()).isNotNull();
        }

        @Test
        @DisplayName("nullのタイトルに変更しようとすると例外が発生すること")
        void changeTitleWithNullShouldThrowException() {
            // Arrange
            final var article = createTestArticle();
            final var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act & Assert
            assertThatThrownBy(() -> {
                article.changeTitle(null, currentDateTime);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Article title cannot be null");
        }
    }

    @Nested
    @DisplayName("本文変更テスト")
    class ChangeBodyTest {

        @Test
        @DisplayName("本文を変更できること")
        void changeBodyWithValidBodyShouldSucceed() {
            // Arrange
            final var article = createTestArticle();
            final var newBody = MarkupContent.markdown("Updated body content");
            final var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act
            final var updated = article.changeBody(newBody, currentDateTime);

            // Assert
            assertThat(updated.body()).isEqualTo(newBody);
            assertThat(updated.updatedAtBusiness()).isNotNull();
        }

        @Test
        @DisplayName("本文をnullに変更すると空の本文になること（本文はnullを持たない）")
        void changeBodyWithNullShouldBecomeEmpty() {
            // Arrange
            final var article = createTestArticle();
            final var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act
            final var updated = article.changeBody(null, currentDateTime);

            // Assert
            assertThat(updated.body()).isEqualTo(MarkupContent.EMPTY);
            assertThat(updated.body().isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("公開・非公開テスト")
    class PublishTest {

        @Test
        @DisplayName("記事を公開できること")
        void publishShouldSetPublicFlag() {
            // Arrange
            final var article = createTestArticle();
            final var currentDateTime = BusinessDateTime.of(Instant.now());
            assertThat(article.isPublic()).isFalse();

            // Act
            final var published = article.publish(currentDateTime);

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
            final var article = createTestArticle();
            final var currentDateTime = BusinessDateTime.of(Instant.now());
            final var firstPublished = article.publish(currentDateTime);
            final var originalPublishedAt = firstPublished.publishedAt();

            // Act
            final var republished = firstPublished.publish(currentDateTime);

            // Assert
            assertThat(republished.publishedAt()).isEqualTo(originalPublishedAt);
        }

        @Test
        @DisplayName("記事を非公開化できること")
        void unpublishShouldUnsetPublicFlag() {
            // Arrange
            final var currentDateTime = BusinessDateTime.of(Instant.now());
            final var article = createTestArticle().publish(currentDateTime);
            assertThat(article.isPublic()).isTrue();

            // Act
            final var unpublished = article.unpublish(currentDateTime);

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
            final var article = Article
                    .create(
                            ArticleType.ALBUM,
                            null,
                            ArticleTitle.of("Album Article"),
                            MarkupContent.plainText("Body"),
                            null,
                            BusinessDateTime.of(Instant.now()));
            final var albumId = Album.Id.generate();
            final var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act
            final var updated = AlbumArticle.from(article)
                    .orElseThrow()
                    .setAlbumId(albumId, currentDateTime);

            // Assert
            assertThat(updated.albumReference().activeAlbumId()).contains(albumId);
            assertThat(updated.updatedAtBusiness()).isNotNull();
        }

        @Test
        @DisplayName("アルバム記事以外はアルバム参照を持つ型として取り出せないこと")
        void nonAlbumArticleCannotBeNarrowedToAlbumArticle() {
            // Arrange
            final var article = Article
                    .create(
                            ArticleType.NOTE,
                            null,
                            ArticleTitle.of("Blog Post"),
                            MarkupContent.plainText("Body"),
                            null,
                            BusinessDateTime.of(Instant.now()));

            // Act & Assert
            assertThat(AlbumArticle.from(article)).isEmpty();
        }
    }

    @Nested
    @DisplayName("記事種別変更テスト")
    class ChangeArticleTypeTest {

        @Test
        @DisplayName("記事種別を変更できること")
        void changeArticleTypeWithValidTypeShouldSucceed() {
            // Arrange
            final var article = Article.create(
                    ArticleType.NOTE,
                    null,
                    ArticleTitle.of("Title"),
                    MarkupContent.plainText("Body"),
                    null,
                    BusinessDateTime.of(Instant.now()));
            final var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act
            final var updated = article.changeArticleType(ArticleType.NEWS, currentDateTime);

            // Assert
            assertThat(updated.articleType()).isEqualTo(ArticleType.NEWS);
            assertThat(updated.updatedAtBusiness()).isNotNull();
        }

        @Test
        @DisplayName("アルバム記事から他の種別に変更するとアルバムIDがクリアされること")
        void changeArticleTypeFromAlbumToOtherShouldClearAlbumId() {
            // Arrange
            final var albumId = Album.Id.generate();
            final var article = Article
                    .create(
                            ArticleType.ALBUM,
                            albumId,
                            ArticleTitle.of("Album Article"),
                            MarkupContent.plainText("Body"),
                            null,
                            BusinessDateTime.of(Instant.now()));
            final var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act
            final var updated = article.changeArticleType(ArticleType.NOTE, currentDateTime);

            // Assert
            assertThat(updated.articleType()).isEqualTo(ArticleType.NOTE);
            assertThat(AlbumArticle.from(updated)).isEmpty();
        }

        @Test
        @DisplayName("nullの記事種別に変更しようとすると例外が発生すること")
        void changeArticleTypeWithNullShouldThrowException() {
            // Arrange
            final var article = createTestArticle();
            final var currentDateTime = BusinessDateTime.of(Instant.now());

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
            final var article = createTestArticle();
            final var tag = createTestTag("TestTag");
            final var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act
            final var updated = article.addTag(tag, currentDateTime);

            // Assert
            assertThat(updated.getTags().size()).isEqualTo(1);
            assertThat(updated.getTags().contains(tag)).isTrue();
            assertThat(updated.updatedAtBusiness()).isNotNull();
        }

        @Test
        @DisplayName("複数のタグを追加できること")
        void addTagWithMultipleTagsShouldSucceed() {
            // Arrange
            final var article = createTestArticle();
            final var tag1 = createTestTag("Tag1");
            final var tag2 = createTestTag("Tag2");
            final var tag3 = createTestTag("Tag3");
            final var currentDateTime = BusinessDateTime.of(Instant.now());

            // Act
            final var updated = article.addTag(tag1, currentDateTime).addTag(tag2, currentDateTime)
                    .addTag(tag3, currentDateTime);

            // Assert
            assertThat(updated.getTags().size()).isEqualTo(3);
            assertThat(updated.getTags()).isEqualTo(
                    List.of(
                            tag1,
                            tag2,
                            tag3));
        }

        @Test
        @DisplayName("nullのタグを追加しようとすると例外が発生すること")
        void addTagWithNullShouldThrowException() {
            // Arrange
            final var article = createTestArticle();
            final var currentDateTime = BusinessDateTime.of(Instant.now());

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
            final var tag1 = createTestTag("Tag1");
            final var tag2 = ArticleTag.reconstruct(tag1.id(), "Tag2"); // 同じIDで異なる名前
            final var currentDateTime = BusinessDateTime.of(Instant.now());
            article = article.addTag(tag1, currentDateTime);

            // Act & Assert
            final var finalArticle = article;
            assertThatThrownBy(() -> {
                finalArticle.addTag(tag2, currentDateTime);
            }).isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("already exists");
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
            final var tag = createTestTag("TagToRemove");
            final var currentDateTime = BusinessDateTime.of(Instant.now());
            article = article.addTag(tag, currentDateTime);

            // Act
            final var updated = article.removeTag(tag.id(), currentDateTime);

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
            final var tag1 = createTestTag("Tag1");
            final var tag2 = createTestTag("Tag2");
            final var tag3 = createTestTag("Tag3");
            final var currentDateTime = BusinessDateTime.of(Instant.now());
            article = article.addTag(tag1, currentDateTime).addTag(tag2, currentDateTime).addTag(tag3, currentDateTime);

            // Act
            final var updated = article.removeTag(tag2.id(), currentDateTime);

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
            final var tag = createTestTag("Tag");
            final var currentDateTime = BusinessDateTime.of(Instant.now());
            article = article.addTag(tag, currentDateTime);
            final var nonExistentId = ArticleTag.Id.generate();

            // Act
            final var updated = article.removeTag(nonExistentId, currentDateTime);

            // Assert
            assertThat(updated.getTags().size()).isEqualTo(1); // タグは削除されていない
        }

        @Test
        @DisplayName("nullのIDでタグを削除しようとすると例外が発生すること")
        void removeTagWithNullIdShouldThrowException() {
            // Arrange
            final var article = createTestArticle();
            final var currentDateTime = BusinessDateTime.of(Instant.now());

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
            final var id = Article.Id.generate();

            // Assert
            assertThat(id).isNotNull();
            assertThat(id.value()).isNotNull();
            assertThat(id.value().isBlank()).isFalse();
        }

        @Test
        @DisplayName("文字列からIDを生成できること")
        void ofWithValidUuidShouldSucceed() {
            // Arrange
            final var validUuid = Article.Id.generate().value();

            // Act
            final var id = Article.Id.of(validUuid);

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
            final var value = Article.Id.generate().value();
            final var id1 = Article.Id.of(value);
            final var id2 = Article.Id.of(value);

            // Act & Assert
            assertThat(id2).isEqualTo(id1);
            assertThat(id2.hashCode()).isEqualTo(id1.hashCode());
        }

        @Test
        @DisplayName("fromInputは有効なUUID文字列で成功すること")
        void fromInputWithValidUuidShouldSucceed() {
            // Arrange
            final var validUuid = Article.Id.generate().value();

            // Act
            final var result = Article.Id.fromInput(validUuid);

            // Assert
            assertThat(result.resolve().value()).isEqualTo(validUuid);
        }

        @Test
        @DisplayName("fromInputはnullでは例外を投げず失敗を返すこと")
        void fromInputWithNullShouldFail() {
            // Act
            final var result = Article.Id.fromInput(null);

            // Assert
            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<Article.Id>) result).errors())
                    .anySatisfy(e -> assertThat(e.code()).isEqualTo("ID_BLANK"));
        }

        @Test
        @DisplayName("fromInputは不正なUUID形式では例外を投げず失敗を返すこと")
        void fromInputWithInvalidUuidShouldFail() {
            // Act
            final var result = Article.Id.fromInput("invalid-uuid");

            // Assert
            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<Article.Id>) result).errors())
                    .anySatisfy(e -> assertThat(e.code()).isEqualTo("ID_INVALID_UUID"));
        }
    }

    // テストヘルパーメソッド

    private Article createTestArticle() {
        return Article.create(
                ArticleType.NOTE,
                null,
                ArticleTitle.of("Test Article"),
                MarkupContent.markdown("Test article body content"),
                null,
                BusinessDateTime.of(Instant.now()));
    }

    private ArticleTag createTestTag(String name) {
        return ArticleTag.create(name);
    }
}
