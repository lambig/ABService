package com.abservice.infrastructure.persistence.entity;

import com.abservice.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * アルバム頒布条件・価格情報エンティティ
 * <p>
 * テーブル: album_distribution
 * </p>
 */
@Entity
@Table(name = "album_distribution")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AlbumDistributionEntity extends AuditableEntity {

    @Id
    @Column(name = "album_id")
    private Long albumId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "album_id")
    private AlbumEntity album;

    @Column(name = "physical_price")
    private Integer physicalPrice;

    @Column(name = "download_price")
    private Integer downloadPrice;

    @Column(name = "demo_url", length = 500)
    private String demoUrl;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
