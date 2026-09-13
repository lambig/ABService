package com.abservice.domain.model.vo.album;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TrackName（トラックの名の解決）のテスト")
class TrackNameTest {

    /** チューン名を繋ぐ区切り。設定値（{@code abservice.track.tune-title-separator}）の既定と揃える */
    private static final String SEPARATOR = " / ";

    /** 名を持たないチューンを間に挟んだ並び */
    private static List<@Nullable String> unnamedInTheMiddle() {
        return Arrays.asList(
                "チューン1",
                null,
                "チューン2");
    }

    @Test
    @DisplayName("タイトルを持つときは、チューンがあってもタイトルが名になる")
    void shouldPreferTheTitle() {
        final var name = TrackName.of(
                "セット名",
                List.of("チューン1", "チューン2"),
                SEPARATOR);

        assertThat(name.value()).isEqualTo("セット名");
    }

    @Test
    @DisplayName("タイトルを持たないときは、チューン名を渡された順に繋いだものが名になる")
    void shouldJoinTuneTitlesWhenTheTitleIsAbsent() {
        final var name = TrackName.of(
                null,
                List.of("チューン1", "チューン2"),
                SEPARATOR);

        assertThat(name.value()).isEqualTo("チューン1 / チューン2");
    }

    @Test
    @DisplayName("名を持たないチューン（MC・環境音）は名に現れない")
    void shouldSkipUnnamedTunes() {
        final var name = TrackName.of(
                null,
                unnamedInTheMiddle(),
                SEPARATOR);

        assertThat(name.value()).isEqualTo("チューン1 / チューン2");
    }

    @Test
    @DisplayName("空白だけのタイトルは、省略と同じに扱う")
    void shouldTreatABlankTitleAsAbsent() {
        final var name = TrackName.of(
                "   ",
                List.of("チューン1"),
                SEPARATOR);

        assertThat(name.value()).isEqualTo("チューン1");
    }

    @Test
    @DisplayName("タイトルも、名を持つチューンも無いときは名を答えられない")
    void shouldFailWhenNothingCanNameTheTrack() {
        assertThatThrownBy(
                () -> TrackName.of(
                        null,
                        Arrays.asList((String) null),
                        SEPARATOR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("区切りは渡されたものを使う（値をコードへ持たない）")
    void shouldUseTheGivenSeparator() {
        final var name = TrackName.of(
                null,
                List.of("A", "B"),
                "、");

        assertThat(name.value()).isEqualTo("A、B");
    }

    @Test
    @DisplayName("名を答えられるかどうかを、生成せずに問える")
    void shouldTellWhetherANameCanBeResolved() {
        assertThat(TrackName.isResolvable("セット名", List.of())).isTrue();
        assertThat(TrackName.isResolvable(null, List.of("チューン1"))).isTrue();
        assertThat(TrackName.isResolvable(null, List.of())).isFalse();
        assertThat(TrackName.isResolvable(null, Arrays.asList((String) null))).isFalse();
    }
}
