package com.abservice.domain.model.vo.album;

/**
 * 入手経路のチャネルタイプを表す列挙型
 *
 * <p>
 * アルバムの入手可能な経路の種類を表します。
 * </p>
 */
public enum ChannelType {
    /**
     * イベント現地
     */
    EVENT,

    /**
     * オンラインショップ（例: メロンブックス、とらのあな）
     */
    ONLINE_SHOP,

    /**
     * ダウンロードサイト（例: BOOTH、Bandcamp）
     */
    DL_SITE,

    /**
     * ストリーミングサービス（例: Spotify、Apple Music）
     */
    STREAMING,

    /**
     * その他
     */
    OTHER
}
