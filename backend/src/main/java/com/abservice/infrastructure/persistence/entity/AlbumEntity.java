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

    // Artist Credit (Value Object)
    @Column(name = "artist_display_name", nullable = false, length = 255)
    private String artistDisplayName;

    @Column(name = "artist_sort_key", length = 255)
    private String artistSortKey;

    // Event Released At (Value Object)
    @Column(name = "event_name", length = 255)
    private String eventName;

    @Column(name = "event_date")
    private LocalDate eventDate;

    @Column(name = "event_place", length = 255)
    private String eventPlace;

    @Column(name = "event_space_number", length = 50)
    private String eventSpaceNumber;

    @Column(name = "event_note", columnDefinition = "TEXT")
    private String eventNote;

    // Event Date and Space (複数日程対応)
    @OneToMany(mappedBy = "album", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AlbumEventDateSpaceEntity> eventDateSpaces = new ArrayList<>();

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
