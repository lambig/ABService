package com.abservice.domain.model.vo.album;

/**
 * 頒布の経路
 *
 * <p>
 * 同じ作品でも、手に取る経路によって額が変わります。経路ごとに額を持たせるのではなく、作品が既定額を
 * 持ち、違う経路だけを上書きする形にします（{@link AlbumPricing}）。
 * </p>
 */
public enum DistributionChannel {
    /** 会場での頒布 */
    VENUE,

    /** 委託での頒布 */
    CONSIGNMENT,

    /** ダウンロードでの頒布 */
    DOWNLOAD
}
