package com.abservice.domain.model.vo.album;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChannelType 列挙型のテスト")
class ChannelTypeTest {

    @DisplayName("values() が全チャネル種別を定義順に返す")
    @Test
    void testEnumValues() {
        assertThat(ChannelType.values()).containsExactly(
                ChannelType.EVENT,
                ChannelType.ONLINE_SHOP,
                ChannelType.DL_SITE,
                ChannelType.STREAMING,
                ChannelType.OTHER);
    }

    @DisplayName("valueOf(\"EVENT\") が EVENT を返す")
    @Test
    void testValueOfEVENT() {
        final ChannelType type = ChannelType.valueOf("EVENT");
        assertThat(type).isEqualTo(ChannelType.EVENT);
    }

    @DisplayName("valueOf(\"ONLINE_SHOP\") が ONLINE_SHOP を返す")
    @Test
    void testValueOfOnlineShop() {
        final ChannelType type = ChannelType.valueOf("ONLINE_SHOP");
        assertThat(type).isEqualTo(ChannelType.ONLINE_SHOP);
    }

    @DisplayName("valueOf(\"DL_SITE\") が DL_SITE を返す")
    @Test
    void testValueOfDlSite() {
        final ChannelType type = ChannelType.valueOf("DL_SITE");
        assertThat(type).isEqualTo(ChannelType.DL_SITE);
    }

    @DisplayName("valueOf(\"STREAMING\") が STREAMING を返す")
    @Test
    void testValueOfSTREAMING() {
        final ChannelType type = ChannelType.valueOf("STREAMING");
        assertThat(type).isEqualTo(ChannelType.STREAMING);
    }

    @DisplayName("valueOf(\"OTHER\") が OTHER を返す")
    @Test
    void testValueOfOTHER() {
        final ChannelType type = ChannelType.valueOf("OTHER");
        assertThat(type).isEqualTo(ChannelType.OTHER);
    }

    @DisplayName("各チャネル種別の name() が定数名と一致する")
    @Test
    void testName() {
        assertThat(ChannelType.EVENT.name()).isEqualTo("EVENT");
        assertThat(ChannelType.ONLINE_SHOP.name()).isEqualTo("ONLINE_SHOP");
        assertThat(ChannelType.DL_SITE.name()).isEqualTo("DL_SITE");
        assertThat(ChannelType.STREAMING.name()).isEqualTo("STREAMING");
        assertThat(ChannelType.OTHER.name()).isEqualTo("OTHER");
    }

    @DisplayName("チャネル種別が5件である")
    @Test
    void testEnumCount() {
        assertThat(ChannelType.values()).hasSize(5);
    }
}
