package com.abservice.domain.model.vo.album;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LabelTagTest {

    @Test
    void testEnumValues() {
        assertThat(LabelTag.values()).containsExactly(LabelTag.NEW, LabelTag.BEST_OF, LabelTag.COMPILATION,
                LabelTag.COLLAB, LabelTag.OTHER);
    }

    @Test
    void testValueOfNEW() {
        LabelTag tag = LabelTag.valueOf("NEW");
        assertThat(tag).isEqualTo(LabelTag.NEW);
    }

    @Test
    void testValueOfBestOf() {
        LabelTag tag = LabelTag.valueOf("BEST_OF");
        assertThat(tag).isEqualTo(LabelTag.BEST_OF);
    }

    @Test
    void testValueOfCOMPILATION() {
        LabelTag tag = LabelTag.valueOf("COMPILATION");
        assertThat(tag).isEqualTo(LabelTag.COMPILATION);
    }

    @Test
    void testValueOfCOLLAB() {
        LabelTag tag = LabelTag.valueOf("COLLAB");
        assertThat(tag).isEqualTo(LabelTag.COLLAB);
    }

    @Test
    void testValueOfOTHER() {
        LabelTag tag = LabelTag.valueOf("OTHER");
        assertThat(tag).isEqualTo(LabelTag.OTHER);
    }

    @Test
    void testName() {
        assertThat(LabelTag.NEW.name()).isEqualTo("NEW");
        assertThat(LabelTag.BEST_OF.name()).isEqualTo("BEST_OF");
        assertThat(LabelTag.COMPILATION.name()).isEqualTo("COMPILATION");
        assertThat(LabelTag.COLLAB.name()).isEqualTo("COLLAB");
        assertThat(LabelTag.OTHER.name()).isEqualTo("OTHER");
    }

    @Test
    void testEnumCount() {
        assertThat(LabelTag.values()).hasSize(5);
    }
}
