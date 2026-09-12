package com.abservice.domain.model.vo.album;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("作品の頒布額")
class AlbumPricingTest {

    @DisplayName("上書きが無ければ、どの経路も既定額になる")
    @Test
    void testFallsBackToStandard() {
        final AlbumPricing pricing = AlbumPricing.of(Price.of(1500));

        assertThat(pricing.at(DistributionChannel.VENUE)).isEqualTo(Price.of(1500));
        assertThat(pricing.at(DistributionChannel.CONSIGNMENT)).isEqualTo(Price.of(1500));
        assertThat(pricing.at(DistributionChannel.DOWNLOAD)).isEqualTo(Price.of(1500));
    }

    @DisplayName("上書きした経路だけが違う額になる")
    @Test
    void testOverriddenChannelOnly() {
        final AlbumPricing pricing = new AlbumPricing(
                Price.of(1500),
                null,
                Price.of(1700),
                Price.of(1000));
        assertThat(pricing.at(DistributionChannel.VENUE)).isEqualTo(Price.of(1500));
        assertThat(pricing.at(DistributionChannel.CONSIGNMENT)).isEqualTo(Price.of(1700));
        assertThat(pricing.at(DistributionChannel.DOWNLOAD)).isEqualTo(Price.of(1000));
    }

    @DisplayName("上書きを持つ経路を判別できる")
    @Test
    void testOverrides() {
        final AlbumPricing pricing = new AlbumPricing(
                Price.of(1500),
                null,
                Price.of(1700),
                null);

        assertThat(pricing.overrides(DistributionChannel.VENUE)).isFalse();
        assertThat(pricing.overrides(DistributionChannel.CONSIGNMENT)).isTrue();
        assertThat(pricing.overrides(DistributionChannel.DOWNLOAD)).isFalse();
    }

    @DisplayName("既定額を持たない生成は例外となる")
    @Test
    void testStandardRequired() {
        assertThatThrownBy(
                () -> new AlbumPricing(
                        null,
                        Price.of(1500),
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Standard price cannot be null");
    }

    @DisplayName("既定額と上書きがすべて同じなら等価")
    @Test
    void testEquivalence() {
        final AlbumPricing pricing = new AlbumPricing(
                Price.of(1500),
                null,
                Price.of(1700),
                null);
        final AlbumPricing same = new AlbumPricing(
                Price.of(1500),
                null,
                Price.of(1700),
                null);

        assertThat(pricing.equivalentTo(same)).isTrue();
        assertThat(pricing.equivalentTo(AlbumPricing.of(Price.of(1500)))).isFalse();
    }
}
