package com.abservice.domain.model.vo.album;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@ResourceLock(Resources.LOCALE)
class IsrcTest {
    @ParameterizedTest
    @ValueSource(strings = {"en-US", "tr-TR", "az-Latn-AZ"})
    @DisplayName("既定ロケールにかかわらず小文字のISRCを同じASCIIへ正規化する")
    void normalizesIndependentlyOfDefaultLocale(String languageTag) {
        final var previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag(languageTag));
            assertThat(new Isrc(" it-abc-12-34567 ").value()).isEqualTo("ITABC1234567");
        } finally {
            Locale.setDefault(previous);
        }
    }
}
