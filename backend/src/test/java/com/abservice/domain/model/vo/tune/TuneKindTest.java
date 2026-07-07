package com.abservice.domain.model.vo.tune;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TuneKind: 楽曲種別の列挙")
class TuneKindTest {

    @DisplayName("values()はTRAD・ORIGINAL・ARRANGEMENTをこの順で返す")
    @Test
    void testEnumValues() {
        assertThat(TuneKind.values()).containsExactly(TuneKind.TRAD, TuneKind.ORIGINAL, TuneKind.ARRANGEMENT);
    }

    @DisplayName("valueOf(\"TRAD\")はTRADを返す")
    @Test
    void testValueOfTRAD() {
        TuneKind kind = TuneKind.valueOf("TRAD");
        assertThat(kind).isEqualTo(TuneKind.TRAD);
    }

    @DisplayName("valueOf(\"ORIGINAL\")はORIGINALを返す")
    @Test
    void testValueOfORIGINAL() {
        TuneKind kind = TuneKind.valueOf("ORIGINAL");
        assertThat(kind).isEqualTo(TuneKind.ORIGINAL);
    }

    @DisplayName("valueOf(\"ARRANGEMENT\")はARRANGEMENTを返す")
    @Test
    void testValueOfARRANGEMENT() {
        TuneKind kind = TuneKind.valueOf("ARRANGEMENT");
        assertThat(kind).isEqualTo(TuneKind.ARRANGEMENT);
    }

    @DisplayName("各要素のname()は定義名の文字列と一致する")
    @Test
    void testName() {
        assertThat(TuneKind.TRAD.name()).isEqualTo("TRAD");
        assertThat(TuneKind.ORIGINAL.name()).isEqualTo("ORIGINAL");
        assertThat(TuneKind.ARRANGEMENT.name()).isEqualTo("ARRANGEMENT");
    }

    @DisplayName("列挙の要素数は3である")
    @Test
    void testEnumCount() {
        assertThat(TuneKind.values()).hasSize(3);
    }
}
