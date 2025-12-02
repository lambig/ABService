package com.abservice.infrastructure.persistence.entity;

import com.abservice.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * アルバムエンティティ
 * <p>
 * テーブル: album
 * </p>
 */
@Entity
@Table(name = "album")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AlbumEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "album_id")
    private Long albumId;

    @Column(name = "domain_id", nullable = false, unique = true, columnDefinition = "UUID")
    private String domainId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "artist_credit_id", nullable = false, columnDefinition = "UUID")
    private String artistCreditId;

    @Column(name = "event_id", columnDefinition = "UUID")
    private String eventId;

    @Column(name = "catalog_number", length = 100)
    private String catalogNumber;

    @OneToMany(mappedBy = "album", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TrackEntity> tracks = new ArrayList<>();

    @OneToOne(mappedBy = "album", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private AlbumArticleEntity albumArticle;

    @OneToOne(mappedBy = "album", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private AlbumDistributionEntity albumDistribution;

    @OneToMany(mappedBy = "album", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AlbumAcquisitionChannelEntity> acquisitionChannels = new ArrayList<>();
}
