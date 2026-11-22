package com.abservice.application.query.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.domain.exception.ValidationException;
import com.abservice.lib.ErrorResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("試聴準備Queryの入力検証")
class ListeningPreparationQueryTest {
    @Test
    @DisplayName("未指定のIDとページ上限を受け入れる")
    void acceptsMissingIdAndPageBoundary() {
        final var validated = new GetListeningPreparationService.Query(42_949_672, null).asValidated();
        assertThat(validated.page()).isEqualTo(42_949_672);
        assertThat(validated.albumId()).isEmpty();
    }

    @Test
    @DisplayName("正準形UUIDの大文字を照会用の小文字へ正規化する")
    void normalizesUppercaseUuid() {
        final var validated = new GetListeningPreparationService.Query(
                0, "ABCDEF01-2345-6789-ABCD-EF0123456789").asValidated();
        assertThat(validated.page()).isZero();
        assertThat(validated.albumId()).contains("abcdef01-2345-6789-abcd-ef0123456789");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 42_949_673, Integer.MAX_VALUE})
    @DisplayName("不正ページをID検証より先に拒否する")
    void rejectsPageBeforeId(int page) {
        assertThatThrownBy(() -> new GetListeningPreparationService.Query(page, "invalid").asValidated())
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        error -> assertThat(error.errors())
                                .containsExactly(
                                        new ErrorResult(
                                                "page",
                                                "有効なページを指定してください",
                                                "INVALID_PAGE")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "invalid", "1-1-1-1-1", " abcdef01-2345-6789-abcd-ef0123456789"})
    @DisplayName("指定された不正IDを既存のエラー内容で拒否する")
    void rejectsNonCanonicalId(String id) {
        assertThatThrownBy(() -> new GetListeningPreparationService.Query(0, id).asValidated())
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        error -> assertThat(error.errors())
                                .containsExactly(
                                        new ErrorResult(
                                                "albumId",
                                                "有効な値を指定してください",
                                                "INVALID_AUDIO_ARGUMENT")));
    }
}
