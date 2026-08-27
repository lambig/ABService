package com.abservice.domain.model.vo.album;

import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TrackTuneTitle値オブジェクト")
class TrackTuneTitleTest {

    @DisplayName("有効なチューン名で生成できる")
    @Test
    void testCreateValidTitle() {
        final TrackTuneTitle title = new TrackTuneTitle("The Butterfly");
        assertThat(title.value()).isEqualTo("The Butterfly");
    }

    @DisplayName("日本語を含むチューン名で生成できる")
    @Test
    void testCreateTitleWithJapanese() {
        final TrackTuneTitle title = new TrackTuneTitle("竹取飛翔 ～ Lunatic Princess");
        assertThat(title.value()).isEqualTo("竹取飛翔 ～ Lunatic Princess");
    }

    @DisplayName("255文字のチューン名で生成できる")
    @Test
    void testCreateTitleMaxLength() {
        final TrackTuneTitle title = new TrackTuneTitle("a".repeat(255));
        assertThat(title.value()).hasSize(255);
    }

    @DisplayName("nullのチューン名は例外を送出する")
    @Test
    void testCreateTitleNull() {
        assertThatThrownBy(() -> new TrackTuneTitle(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tune title cannot be blank");
    }

    @DisplayName("空白のみのチューン名は例外を送出する")
    @Test
    void testCreateTitleBlank() {
        assertThatThrownBy(() -> new TrackTuneTitle("   ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tune title cannot be blank");
    }

    @DisplayName("256文字以上のチューン名は例外を送出する")
    @Test
    void testCreateTitleTooLong() {
        assertThatThrownBy(() -> new TrackTuneTitle("a".repeat(256))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tune title must be 255 characters or less");
    }

    @DisplayName("外部入力からの生成は成功時にSuccessを返す")
    @Test
    void testFromInputSuccess() {
        final var result = TrackTuneTitle.fromInput("The Butterfly");

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.resolve().value()).isEqualTo("The Butterfly");
    }

    @DisplayName("外部入力が未指定なら例外ではなくFailureを返す")
    @Test
    void testFromInputBlankFails() {
        final var result = TrackTuneTitle.fromInput("   ");

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .containsExactly("TRACK_TUNE_TITLE_REQUIRED");
    }

    @DisplayName("外部入力が最大長を超えるならFailureを返す")
    @Test
    void testFromInputTooLongFails() {
        final var result = TrackTuneTitle.fromInput("a".repeat(256));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .containsExactly("TRACK_TUNE_TITLE_TOO_LONG");
    }

    @DisplayName("同じ値同士はequivalentToがtrueを返す")
    @Test
    void testEquivalentToSame() {
        assertThat(new TrackTuneTitle("Tune").equivalentTo(new TrackTuneTitle("Tune"))).isTrue();
    }

    @DisplayName("異なる値同士はequivalentToがfalseを返す")
    @Test
    void testEquivalentToDifferent() {
        assertThat(new TrackTuneTitle("Tune A").equivalentTo(new TrackTuneTitle("Tune B"))).isFalse();
    }

    @DisplayName("nullとのequivalentToはfalseを返す")
    @Test
    void testEquivalentToNull() {
        assertThat(new TrackTuneTitle("Tune").equivalentTo(null)).isFalse();
    }

    @DisplayName("同じ値は等価でhashCodeも一致する")
    @Test
    void testEquality() {
        final TrackTuneTitle title1 = new TrackTuneTitle("Tune");
        final TrackTuneTitle title2 = new TrackTuneTitle("Tune");

        assertThat(title1).isEqualTo(title2);
        assertThat(title1.hashCode()).isEqualTo(title2.hashCode());
        assertThat(title1).isNotEqualTo(new TrackTuneTitle("Different"));
    }
}
