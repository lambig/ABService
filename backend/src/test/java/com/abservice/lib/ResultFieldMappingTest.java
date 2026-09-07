package com.abservice.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResultFieldMappingTest {

    @Test
    @DisplayName("複数エラーの位置を配列入力へ対応付け、順序と内容を保持する")
    void mapsNestedFieldPaths() {
        final var result = Result.failure(
                List.of(
                        new ErrorResult(
                                "title",
                                "必須",
                                "REQUIRED"),
                        new ErrorResult(
                                "tunes[1].title",
                                "長すぎる",
                                "LONG")));
        assertThat(result.mapErrorFields(field -> "tracks[0]." + field).errors())
                .containsExactly(
                        new ErrorResult(
                                "tracks[0].title",
                                "必須",
                                "REQUIRED"),
                        new ErrorResult(
                                "tracks[0].tunes[1].title",
                                "長すぎる",
                                "LONG"));
        assertThat(result.errors().getFirst().field()).isEqualTo("title");
    }

    @Test
    @DisplayName("成功値にはエラー変換を適用しない")
    void keepsSuccessfulValue() {
        final var result = Result.success("value");
        assertThat(result.mapErrorFields(field -> "unused")).isSameAs(result);
    }
}
