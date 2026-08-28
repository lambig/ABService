package com.abservice.domain.model.entity.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ArticleTag（記事タグ）のテスト")
class ArticleTagTest {

    @Nested
    @DisplayName("生成テスト")
    class CreateTest {

        @Test
        @DisplayName("名前を与えると新しいIDのタグを生成できること")
        void createWithNameShouldSucceed() {
            final var tag = ArticleTag.create("ライブ");

            assertThat(tag.getName()).isEqualTo("ライブ");
            assertThat(tag.id().value()).isNotBlank();
        }

        @Test
        @DisplayName("名前が空白なら例外になること")
        void createWithBlankNameShouldFail() {
            assertThatThrownBy(() -> ArticleTag.create("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("外部入力からの生成テスト")
    class FromInputTest {

        @Test
        @DisplayName("名前が妥当なら成功すること")
        void validNameSucceeds() {
            final var result = ArticleTag.fromInput("セッション");

            assertThat(result).isInstanceOf(Result.Success.class);
            assertThat(result.resolve().getName()).isEqualTo("セッション");
        }

        @Test
        @DisplayName("名前が未指定なら必須エラーになること")
        void blankNameFails() {
            final var result = ArticleTag.fromInput(" ");

            assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                    .contains("TAG_NAME_REQUIRED");
        }

        @Test
        @DisplayName("名前が最大長を超えるなら長さエラーになること")
        void tooLongNameFails() {
            final var result = ArticleTag.fromInput("あ".repeat(101));

            assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                    .contains("TAG_NAME_TOO_LONG");
        }

        @Test
        @DisplayName("名前が最大長ちょうどなら成功すること")
        void maxLengthNameSucceeds() {
            final var result = ArticleTag.fromInput("あ".repeat(100));

            assertThat(result).isInstanceOf(Result.Success.class);
        }
    }

    @Nested
    @DisplayName("タグIDのテスト")
    class IdTest {

        @Test
        @DisplayName("UUID形式でない文字列は検証エラーになること")
        void invalidUuidFails() {
            final var result = ArticleTag.Id.fromInput("not-a-uuid");

            assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                    .contains("ID_INVALID_UUID");
        }

        @Test
        @DisplayName("生成したIDは外部入力としても妥当であること")
        void generatedIdIsValidAsInput() {
            final var generated = ArticleTag.Id.generate();

            final var result = ArticleTag.Id.fromInput(generated.value());

            assertThat(result).isInstanceOf(Result.Success.class);
            assertThat(result.resolve()).isEqualTo(generated);
        }
    }
}
