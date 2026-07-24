package com.abservice.infrastructure.persistence.entity;

import com.abservice.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * アルバム頒布条件・価格情報テーブルレコード
 * <p>
 * テーブル: album_distribution
 * </p>
 */
@Entity
@Table(name = "album_distribution")
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class AlbumDistributionTableRecord extends AuditableEntity {

    @Id
    @Column(name = "album_id")
    private Long albumId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "album_id")
    private AlbumTableRecord album;

    @Column(name = "physical_price")
    private Integer physicalPrice;

    @Column(name = "download_price")
    private Integer downloadPrice;

    @Column(name = "demo_url", length = 500)
    private String demoUrl;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
