package com.abservice.domain.model.vo.album;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LabelTag（レーベルタグ）のテスト")
class LabelTagTest {

    @DisplayName("values()は全ての要素を定義順に返す")
    @Test
    void testEnumValues() {
        assertThat(LabelTag.values()).containsExactly(LabelTag.NEW, LabelTag.BEST_OF, LabelTag.COMPILATION,
                LabelTag.COLLAB, LabelTag.OTHER);
    }

    @DisplayName("valueOf(\"NEW\")はNEWを返す")
    @Test
    void testValueOfNEW() {
        LabelTag tag = LabelTag.valueOf("NEW");
        assertThat(tag).isEqualTo(LabelTag.NEW);
    }

    @DisplayName("valueOf(\"BEST_OF\")はBEST_OFを返す")
    @Test
    void testValueOfBestOf() {
        LabelTag tag = LabelTag.valueOf("BEST_OF");
        assertThat(tag).isEqualTo(LabelTag.BEST_OF);
    }

    @DisplayName("valueOf(\"COMPILATION\")はCOMPILATIONを返す")
    @Test
    void testValueOfCOMPILATION() {
        LabelTag tag = LabelTag.valueOf("COMPILATION");
        assertThat(tag).isEqualTo(LabelTag.COMPILATION);
    }

    @DisplayName("valueOf(\"COLLAB\")はCOLLABを返す")
    @Test
    void testValueOfCOLLAB() {
        LabelTag tag = LabelTag.valueOf("COLLAB");
        assertThat(tag).isEqualTo(LabelTag.COLLAB);
    }

    @DisplayName("valueOf(\"OTHER\")はOTHERを返す")
    @Test
    void testValueOfOTHER() {
        LabelTag tag = LabelTag.valueOf("OTHER");
        assertThat(tag).isEqualTo(LabelTag.OTHER);
    }

    @DisplayName("各要素のname()は定数名の文字列を返す")
    @Test
    void testName() {
        assertThat(LabelTag.NEW.name()).isEqualTo("NEW");
        assertThat(LabelTag.BEST_OF.name()).isEqualTo("BEST_OF");
        assertThat(LabelTag.COMPILATION.name()).isEqualTo("COMPILATION");
        assertThat(LabelTag.COLLAB.name()).isEqualTo("COLLAB");
        assertThat(LabelTag.OTHER.name()).isEqualTo("OTHER");
    }

    @DisplayName("要素数は5である")
    @Test
    void testEnumCount() {
        assertThat(LabelTag.values()).hasSize(5);
    }
}
