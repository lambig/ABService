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
    @DisplayName("どの項目にも紐付かない空の field は、写像でも位置の固定でも項目のエラーにしない")
    void keepsWholeRequestErrorUnassigned() {
        final var result = Result.failure(
                List.of(
                        new ErrorResult(
                                "",
                                "組み合わせが不正です",
                                "COMBINATION_INVALID"),
                        new ErrorResult(
                                "title",
                                "必須",
                                "REQUIRED")));

        assertThat(result.mapErrorFields(field -> "tracks[0]." + field).errors())
                .containsExactly(
                        new ErrorResult(
                                "",
                                "組み合わせが不正です",
                                "COMBINATION_INVALID"),
                        new ErrorResult(
                                "tracks[0].title",
                                "必須",
                                "REQUIRED"));
        assertThat(result.withErrorField("orderedTrackIds[0]").errors())
                .containsExactly(
                        new ErrorResult(
                                "",
                                "組み合わせが不正です",
                                "COMBINATION_INVALID"),
                        new ErrorResult(
                                "orderedTrackIds[0]",
                                "必須",
                                "REQUIRED"));
    }

    @Test
    @DisplayName("成功値にはエラー変換を適用しない")
    void keepsSuccessfulValue() {
        final var result = Result.success("value");
        assertThat(result.mapErrorFields(field -> "unused")).isSameAs(result);
        assertThat(result.withErrorField("unused")).isSameAs(result);
    }

    @Test
    @DisplayName("位置の固定は、値オブジェクトが返した field を捨てて全エラーを1つのパスへ寄せる")
    void fixesEveryErrorToOnePath() {
        final var result = Result.failure(
                List.of(
                        new ErrorResult(
                                "value",
                                "空です",
                                "ID_BLANK"),
                        new ErrorResult(
                                "value",
                                "UUIDではありません",
                                "ID_INVALID_UUID")));

        assertThat(result.withErrorField("orderedTrackIds[2]").errors())
                .containsExactly(
                        new ErrorResult(
                                "orderedTrackIds[2]",
                                "空です",
                                "ID_BLANK"),
                        new ErrorResult(
                                "orderedTrackIds[2]",
                                "UUIDではありません",
                                "ID_INVALID_UUID"));
        assertThat(result.errors().getFirst().field()).isEqualTo("value");
    }
}
