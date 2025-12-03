package com.abservice.domain.model.vo.tune;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TuneKindTest {

    @Test
    void testEnumValues() {
        assertThat(TuneKind.values()).containsExactly(TuneKind.TRAD, TuneKind.ORIGINAL, TuneKind.ARRANGEMENT);
    }

    @Test
    void testValueOfTRAD() {
        TuneKind kind = TuneKind.valueOf("TRAD");
        assertThat(kind).isEqualTo(TuneKind.TRAD);
    }

    @Test
    void testValueOfORIGINAL() {
        TuneKind kind = TuneKind.valueOf("ORIGINAL");
        assertThat(kind).isEqualTo(TuneKind.ORIGINAL);
    }

    @Test
    void testValueOfARRANGEMENT() {
        TuneKind kind = TuneKind.valueOf("ARRANGEMENT");
        assertThat(kind).isEqualTo(TuneKind.ARRANGEMENT);
    }

    @Test
    void testName() {
        assertThat(TuneKind.TRAD.name()).isEqualTo("TRAD");
        assertThat(TuneKind.ORIGINAL.name()).isEqualTo("ORIGINAL");
        assertThat(TuneKind.ARRANGEMENT.name()).isEqualTo("ARRANGEMENT");
    }

    @Test
    void testEnumCount() {
        assertThat(TuneKind.values()).hasSize(3);
    }
}
