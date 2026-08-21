package com.abservice.domain.model.vo.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AssetKey（アセットキーVO）のテスト")
class AssetKeyTest {

    private static final String VALID_KEY = "01a0233d-d25a-7c3b-924f-236ee154fecc.png";

    @Test
    @DisplayName("アップロード基盤が払い出す形式のキーを受け入れる")
    void acceptsIssuedKey() {
        assertThat(AssetKey.of(VALID_KEY).value()).isEqualTo(VALID_KEY);
    }

    @Test
    @DisplayName("空白のキーは拒否する")
    void rejectsBlank() {
        assertThat(AssetKey.fromInput("   ")).isInstanceOf(Result.Failure.class);
        assertThat(codesOf(AssetKey.fromInput(null))).contains("ASSET_KEY_REQUIRED");
    }

    @Test
    @DisplayName("パス区切りを含むキーは拒否する")
    void rejectsPathSeparators() {
        assertThat(codesOf(AssetKey.fromInput("assets/" + VALID_KEY))).contains("ASSET_KEY_INVALID_FORMAT");
        assertThat(codesOf(AssetKey.fromInput("../" + VALID_KEY))).contains("ASSET_KEY_INVALID_FORMAT");
    }

    @Test
    @DisplayName("配信URLをそのまま渡すと拒否する")
    void rejectsDeliveryUrl() {
        assertThat(codesOf(AssetKey.fromInput("/assets/" + VALID_KEY))).contains("ASSET_KEY_INVALID_FORMAT");
        assertThat(codesOf(AssetKey.fromInput("https://example.com/assets/" + VALID_KEY)))
                .contains("ASSET_KEY_INVALID_FORMAT");
    }

    @Test
    @DisplayName("最大長を超えるキーは拒否する")
    void rejectsTooLong() {
        assertThat(codesOf(AssetKey.fromInput("a".repeat(256)))).contains("ASSET_KEY_TOO_LONG");
    }

    @Test
    @DisplayName("不正なキーの内部生成は例外にする")
    void throwsOnInvalidInternalCreation() {
        assertThatThrownBy(() -> AssetKey.of("assets/" + VALID_KEY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("同じ値のキーは等価とみなす")
    void equivalentByValue() {
        assertThat(AssetKey.of(VALID_KEY).equivalentTo(AssetKey.of(VALID_KEY))).isTrue();
        assertThat(AssetKey.of(VALID_KEY).equivalentTo(AssetKey.of("other.png"))).isFalse();
    }

    private static List<String> codesOf(Result<AssetKey> result) {
        return ((Result.Failure<AssetKey>) result).errors().stream().map(ErrorResult::code).toList();
    }
}
