package com.abservice.domain.model.vo.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.lib.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SiteContentKey値オブジェクトのテスト")
class SiteContentKeyTest {

    @Nested
    @DisplayName("受け入れる形")
    class AcceptedFormatTest {

        @Test
        @DisplayName("2セグメントのキーを生成できること")
        void twoSegmentsShouldSucceed() {
            assertThat(SiteContentKey.of("site.name").value()).isEqualTo("site.name");
        }

        @Test
        @DisplayName("3セグメント以上のキーを生成できること")
        void moreSegmentsShouldSucceed() {
            assertThat(SiteContentKey.of("home.hero.introduction").value()).isEqualTo("home.hero.introduction");
        }

        @Test
        @DisplayName("セグメントに数字を含められること")
        void digitsInSegmentShouldSucceed() {
            assertThat(SiteContentKey.of("home.block2").value()).isEqualTo("home.block2");
        }
    }

    @Nested
    @DisplayName("拒否する形")
    class RejectedFormatTest {

        @Test
        @DisplayName("空白のみは拒否されること")
        void blankShouldFail() {
            assertThatThrownBy(() -> SiteContentKey.of("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("1セグメントは拒否されること（区切りのない名前は用途が読めない）")
        void singleSegmentShouldFail() {
            assertThatThrownBy(() -> SiteContentKey.of("site"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("大文字を含むキーは拒否されること")
        void uppercaseShouldFail() {
            assertThatThrownBy(() -> SiteContentKey.of("Site.Name"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("アンダースコア区切りは拒否されること")
        void underscoreShouldFail() {
            assertThatThrownBy(() -> SiteContentKey.of("site_name"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("ドットで始まる・終わるキーは拒否されること")
        void leadingOrTrailingDotShouldFail() {
            assertThatThrownBy(() -> SiteContentKey.of(".site.name"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SiteContentKey.of("site.name."))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("連続するドットは拒否されること")
        void consecutiveDotsShouldFail() {
            assertThatThrownBy(() -> SiteContentKey.of("site..name"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("数字で始まるセグメントは拒否されること")
        void segmentStartingWithDigitShouldFail() {
            assertThatThrownBy(() -> SiteContentKey.of("site.2block"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("fromInput（外部入力からの生成）")
    class FromInputTest {

        @Test
        @DisplayName("有効なキーで成功する")
        void validKeyShouldSucceed() {
            final Result<SiteContentKey> result = SiteContentKey.fromInput("site.description");

            assertThat(result.resolve().value()).isEqualTo("site.description");
        }

        @Test
        @DisplayName("形式違反は不正エラーになる")
        void invalidFormatShouldFail() {
            final Result<SiteContentKey> result = SiteContentKey.fromInput("SiteName");

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<SiteContentKey>) result).errors())
                    .anySatisfy(e -> assertThat(e.code()).isEqualTo("SITE_CONTENT_KEY_INVALID_FORMAT"));
        }

        @Test
        @DisplayName("未指定は必須エラーになる")
        void nullShouldFailAsRequired() {
            final Result<SiteContentKey> result = SiteContentKey.fromInput(null);

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<SiteContentKey>) result).errors())
                    .anySatisfy(e -> assertThat(e.code()).isEqualTo("SITE_CONTENT_KEY_REQUIRED"));
        }

        @Test
        @DisplayName("最大長を超えると長さエラーになる")
        void tooLongShouldFail() {
            final var longKey = "site." + "a".repeat(100);

            final Result<SiteContentKey> result = SiteContentKey.fromInput(longKey);

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<SiteContentKey>) result).errors())
                    .anySatisfy(e -> assertThat(e.code()).isEqualTo("SITE_CONTENT_KEY_TOO_LONG"));
        }
    }

    @Nested
    @DisplayName("等価性")
    class EquivalenceTest {

        @Test
        @DisplayName("同じ値は等価であること")
        void sameValueShouldBeEquivalent() {
            assertThat(SiteContentKey.of("site.name").equivalentTo(SiteContentKey.of("site.name"))).isTrue();
        }

        @Test
        @DisplayName("異なる値は等価でないこと")
        void differentValueShouldNotBeEquivalent() {
            assertThat(SiteContentKey.of("site.name").equivalentTo(SiteContentKey.of("site.description"))).isFalse();
        }

        @Test
        @DisplayName("nullとの比較は等価でないこと")
        void nullShouldNotBeEquivalent() {
            assertThat(SiteContentKey.of("site.name").equivalentTo(null)).isFalse();
        }
    }
}
