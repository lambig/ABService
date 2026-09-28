package com.abservice.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 確定した画像アセットの実測値の読取用レコード。記録は upsert の SQL を持つ専用アダプタへ限定する。 */
@Entity
@Table(name = "published_asset")
@Getter
@NoArgsConstructor
public class PublishedAssetTableRecord {
    @Id
    @Column(name = "asset_key", length = 255)
    private String assetKey;
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;
    @Column(name = "byte_length", nullable = false)
    private long byteLength;
    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;
    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;
}
