package com.abservice.domain.model.vo.album;

import com.abservice.lib.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("原作の出典の記述")
class OriginalWorkNoteTest {

    /** 想定している綴り。作品名と言い回しがひとつながりで入る（#365） */
    private static final String SAMPLE = "「○○」より各曲";

    @DisplayName("人が書いた一文をそのまま保持する")
    @Test
    void testCreateValidNote() {
        final OriginalWorkNote note = new OriginalWorkNote(SAMPLE);
        assertThat(note.value()).isEqualTo(SAMPLE);
    }

    @DisplayName("言い回しが盤ごとに違っても、そのまま保持する")
    @Test
    void testCreateNoteWithDifferentWording() {
        final OriginalWorkNote note = new OriginalWorkNote("「○○」のメインテーマのみ");
        assertThat(note.value()).isEqualTo("「○○」のメインテーマのみ");
    }

    @DisplayName("最大長255文字の記述を生成できる")
    @Test
    void testCreateNoteMaxLength() {
        final OriginalWorkNote note = new OriginalWorkNote("あ".repeat(255));
        assertThat(note.value()).hasSize(255);
    }

    @DisplayName("nullの記述は例外となる")
    @Test
    void testCreateNoteNull() {
        assertThatThrownBy(() -> new OriginalWorkNote(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Original work note cannot be blank");
    }

    @DisplayName("空白のみの記述は例外となる")
    @Test
    void testCreateNoteBlank() {
        assertThatThrownBy(() -> new OriginalWorkNote("   ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Original work note cannot be blank");
    }

    @DisplayName("255文字を超える記述は例外となる")
    @Test
    void testCreateNoteTooLong() {
        assertThatThrownBy(() -> new OriginalWorkNote("あ".repeat(256)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Original work note must be 255 characters or less");
    }

    @DisplayName("外部入力から生成できる")
    @Test
    void testFromInput() {
        assertThat(OriginalWorkNote.fromInput(SAMPLE).orElse(null)).isEqualTo(new OriginalWorkNote(SAMPLE));
    }

    @DisplayName("外部入力が空白のみなら、例外ではなく失敗を返す")
    @Test
    void testFromInputBlankFails() {
        final Result<OriginalWorkNote> result = OriginalWorkNote.fromInput("  ");

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<OriginalWorkNote>) result).errors().getFirst().code())
                .isEqualTo("ORIGINAL_WORK_NOTE_BLANK");
    }

    @DisplayName("外部入力が最大長を超えるなら、例外ではなく失敗を返す")
    @Test
    void testFromInputTooLongFails() {
        final Result<OriginalWorkNote> result = OriginalWorkNote.fromInput("あ".repeat(256));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<OriginalWorkNote>) result).errors().getFirst().code())
                .isEqualTo("ORIGINAL_WORK_NOTE_TOO_LONG");
    }

    @DisplayName("同じ値の記述は同等と判定される")
    @Test
    void testEquivalentToSame() {
        assertThat(new OriginalWorkNote(SAMPLE).equivalentTo(new OriginalWorkNote(SAMPLE))).isTrue();
    }

    @DisplayName("異なる値の記述は同等でないと判定される")
    @Test
    void testEquivalentToDifferent() {
        assertThat(new OriginalWorkNote(SAMPLE).equivalentTo(new OriginalWorkNote("「××」より各曲"))).isFalse();
    }

    @DisplayName("nullとの同等判定はfalseとなる")
    @Test
    void testEquivalentToNull() {
        assertThat(new OriginalWorkNote(SAMPLE).equivalentTo(null)).isFalse();
    }
}
