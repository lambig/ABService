package com.abservice.domain.model.aggregate.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.domain.model.vo.common.MarkupContent;
import com.abservice.domain.model.vo.site.SiteContentKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SiteContent集約のテスト")
class SiteContentTest {

    private static final SiteContentKey KEY = SiteContentKey.of("home.introduction");

    @Nested
    @DisplayName("生成テスト")
    class CreateTest {

        @Test
        @DisplayName("キーと文言で生成できること")
        void createShouldSucceed() {
            // Act
            final var content = SiteContent.create(KEY, MarkupContent.markdown("## 紹介"));

            // Assert
            assertThat(content.key()).isEqualTo(KEY);
            assertThat(content.content().content()).isEqualTo("## 紹介");
            assertThat(content.id().value()).isNotBlank();
        }

        @Test
        @DisplayName("キーがnullでは例外が発生すること")
        void nullKeyShouldThrow() {
            assertThatThrownBy(() -> SiteContent.create(null, MarkupContent.markdown("x")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("文言がnullでは例外が発生すること")
        void nullContentShouldThrow() {
            assertThatThrownBy(() -> SiteContent.create(KEY, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("文言の差し替えテスト")
    class WithContentTest {

        @Test
        @DisplayName("文言を差し替えられること")
        void withContentShouldReplaceContent() {
            // Arrange
            final var original = SiteContent.create(KEY, MarkupContent.markdown("旧"));

            // Act
            final var updated = original.withContent(MarkupContent.markdown("新"));

            // Assert
            assertThat(updated.content().content()).isEqualTo("新");
        }

        @Test
        @DisplayName("差し替えてもキーとIDは変わらないこと")
        void withContentShouldKeepKeyAndId() {
            // Arrange
            final var original = SiteContent.create(KEY, MarkupContent.markdown("旧"));

            // Act
            final var updated = original.withContent(MarkupContent.plainText("新"));

            // Assert
            assertThat(updated.key()).isEqualTo(original.key());
            assertThat(updated.id()).isEqualTo(original.id());
        }

        @Test
        @DisplayName("マークアップ形式も差し替わること")
        void withContentShouldReplaceFormat() {
            // Arrange
            final var original = SiteContent.create(KEY, MarkupContent.markdown("旧"));

            // Act
            final var updated = original.withContent(MarkupContent.plainText("新"));

            // Assert
            assertThat(updated.content().format()).isEqualTo(MarkupContent.plainText("新").format());
        }
    }

    @Nested
    @DisplayName("同一性テスト")
    class IdentityTest {

        @Test
        @DisplayName("IDが同じなら等価であること（文言が違っても同一の集約）")
        void sameIdShouldBeEquivalent() {
            // Arrange
            final var original = SiteContent.create(KEY, MarkupContent.markdown("旧"));

            // Act
            final var updated = original.withContent(MarkupContent.markdown("新"));

            // Assert
            assertThat(original.equivalentTo(updated)).isTrue();
        }

        @Test
        @DisplayName("IDが異なれば等価でないこと（同じキーでも別の集約）")
        void differentIdShouldNotBeEquivalent() {
            // Arrange
            final var one = SiteContent.create(KEY, MarkupContent.markdown("x"));
            final var another = SiteContent.create(KEY, MarkupContent.markdown("x"));

            // Act & Assert
            assertThat(one.equivalentTo(another)).isFalse();
        }
    }
}
