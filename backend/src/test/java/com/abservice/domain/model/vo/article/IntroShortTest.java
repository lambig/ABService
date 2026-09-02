package com.abservice.domain.model.vo.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.lib.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("IntroShort値オブジェクトのテスト")
class IntroShortTest {

    @DisplayName("有効な紹介文で生成できる")
    @Test
    void testCreateValidIntroShort() {
        final IntroShort introShort = IntroShort.of("新譜のお知らせ");
        assertThat(introShort.value()).isEqualTo("新譜のお知らせ");
    }

    @DisplayName("最大長120文字の紹介文で生成できる")
    @Test
    void testCreateIntroShortMaxLength() {
        final IntroShort introShort = IntroShort.of("a".repeat(120));
        assertThat(introShort.value()).hasSize(120);
    }

    @DisplayName("空文字の紹介文で生成できる（空はあり得る）")
    @Test
    void testCreateIntroShortEmpty() {
        assertThat(IntroShort.of("").isEmpty()).isTrue();
    }

    @DisplayName("空白のみの紹介文は空として扱う")
    @Test
    void testCreateIntroShortBlank() {
        assertThat(IntroShort.of("   ").isEmpty()).isTrue();
    }

    @DisplayName("EMPTYは空の紹介文である")
    @Test
    void testEmptyConstant() {
        assertThat(IntroShort.EMPTY.value()).isEmpty();
        assertThat(IntroShort.EMPTY.isEmpty()).isTrue();
    }

    @DisplayName("nullの紹介文は例外となる")
    @Test
    void testCreateIntroShortNull() {
        assertThatThrownBy(() -> IntroShort.of(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ショート紹介文にnullは指定できません");
    }

    @DisplayName("121文字以上の紹介文は例外となる")
    @Test
    void testCreateIntroShortTooLong() {
        assertThatThrownBy(() -> IntroShort.of("a".repeat(121))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ショート紹介文は120文字以内です");
    }

    @DisplayName("同じ値同士はequivalentToがtrueとなる")
    @Test
    void testEquivalentToSame() {
        assertThat(IntroShort.of("紹介文").equivalentTo(IntroShort.of("紹介文"))).isTrue();
    }

    @DisplayName("異なる値同士はequivalentToがfalseとなる")
    @Test
    void testEquivalentToDifferent() {
        assertThat(IntroShort.of("紹介A").equivalentTo(IntroShort.of("紹介B"))).isFalse();
    }

    @DisplayName("nullとのequivalentToはfalseとなる")
    @Test
    void testEquivalentToNull() {
        assertThat(IntroShort.of("紹介文").equivalentTo(null)).isFalse();
    }

    @Nested
    @DisplayName("fromInput（外部入力からの生成）")
    class FromInputTest {

        @Test
        @DisplayName("有効な紹介文で成功する")
        void validIntroShortShouldSucceed() {
            final Result<IntroShort> result = IntroShort.fromInput("新譜のお知らせ");
            assertThat(result.resolve().value()).isEqualTo("新譜のお知らせ");
        }

        @Test
        @DisplayName("nullは紹介文なし（EMPTY）になる")
        void nullShouldBecomeEmpty() {
            assertThat(IntroShort.fromInput(null).resolve()).isEqualTo(IntroShort.EMPTY);
        }

        @Test
        @DisplayName("空文字は紹介文なし（EMPTY）になる")
        void emptyShouldBecomeEmpty() {
            assertThat(IntroShort.fromInput("").resolve()).isEqualTo(IntroShort.EMPTY);
        }

        @Test
        @DisplayName("空白のみは紹介文なし（EMPTY）になる")
        void blankShouldBecomeEmpty() {
            assertThat(IntroShort.fromInput("   ").resolve()).isEqualTo(IntroShort.EMPTY);
        }

        @Test
        @DisplayName("121文字以上は長さ超過エラーになる")
        void tooLongShouldFailAsTooLong() {
            final Result<IntroShort> result = IntroShort.fromInput("a".repeat(121));
            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<IntroShort>) result).errors()).singleElement()
                    .satisfies(e -> {
                        assertThat(e.field()).isEqualTo("introShort");
                        assertThat(e.code()).isEqualTo("ARTICLE_INTRO_SHORT_TOO_LONG");
                    });
        }
    }
}
