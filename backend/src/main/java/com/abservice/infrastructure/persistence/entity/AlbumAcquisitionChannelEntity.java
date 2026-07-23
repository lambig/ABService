package com.abservice.infrastructure.persistence.entity;

import com.abservice.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * アルバム入手経路エンティティ
 * <p>
 * テーブル: album_acquisition_channel
 * </p>
 */
@Entity
@Table(name = "album_acquisition_channel")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AlbumAcquisitionChannelEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "album_acquisition_id")
    private Long albumAcquisitionId;

    @Column(name = "domain_id", nullable = false, unique = true)
    private String domainId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id", nullable = false)
    private AlbumEntity album;

    @Column(name = "channel_type", nullable = false, length = 50)
    private String channelType;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "url", length = 500)
    private String url;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
