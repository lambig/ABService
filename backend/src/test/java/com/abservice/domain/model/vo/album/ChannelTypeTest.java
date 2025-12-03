package com.abservice.domain.model.vo.album;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelTypeTest {

    @Test
    void testEnumValues() {
        assertThat(ChannelType.values()).containsExactly(ChannelType.EVENT, ChannelType.ONLINE_SHOP,
                ChannelType.DL_SITE, ChannelType.STREAMING, ChannelType.OTHER);
    }

    @Test
    void testValueOfEVENT() {
        ChannelType type = ChannelType.valueOf("EVENT");
        assertThat(type).isEqualTo(ChannelType.EVENT);
    }

    @Test
    void testValueOfOnlineShop() {
        ChannelType type = ChannelType.valueOf("ONLINE_SHOP");
        assertThat(type).isEqualTo(ChannelType.ONLINE_SHOP);
    }

    @Test
    void testValueOfDlSite() {
        ChannelType type = ChannelType.valueOf("DL_SITE");
        assertThat(type).isEqualTo(ChannelType.DL_SITE);
    }

    @Test
    void testValueOfSTREAMING() {
        ChannelType type = ChannelType.valueOf("STREAMING");
        assertThat(type).isEqualTo(ChannelType.STREAMING);
    }

    @Test
    void testValueOfOTHER() {
        ChannelType type = ChannelType.valueOf("OTHER");
        assertThat(type).isEqualTo(ChannelType.OTHER);
    }

    @Test
    void testName() {
        assertThat(ChannelType.EVENT.name()).isEqualTo("EVENT");
        assertThat(ChannelType.ONLINE_SHOP.name()).isEqualTo("ONLINE_SHOP");
        assertThat(ChannelType.DL_SITE.name()).isEqualTo("DL_SITE");
        assertThat(ChannelType.STREAMING.name()).isEqualTo("STREAMING");
        assertThat(ChannelType.OTHER.name()).isEqualTo("OTHER");
    }

    @Test
    void testEnumCount() {
        assertThat(ChannelType.values()).hasSize(5);
    }
}
