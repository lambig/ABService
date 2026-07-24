package com.abservice.infrastructure.persistence.entity;

import com.abservice.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * トラック（録音単位）テーブルレコード
 * <p>
 * テーブル: track
 * </p>
 */
@Entity
@Table(name = "track")
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class TrackTableRecord extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "track_id")
    private Long trackId;

    @Column(name = "domain_id", nullable = false, unique = true)
    private String domainId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id", nullable = false)
    private AlbumTableRecord album;

    @Column(name = "track_no", nullable = false)
    private Integer trackNo;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    // Artist Credit (Value Object) - nullable: nullの場合はAlbumのartistCreditを継承
    @Column(name = "artist_display_name", length = 255)
    private String artistDisplayName;

    @Column(name = "artist_sort_key", length = 255)
    private String artistSortKey;

    @Column(name = "recording_date")
    private LocalDate recordingDate;

    @Column(name = "recording_place", length = 255)
    private String recordingPlace;

    @Column(name = "is_live")
    private Boolean isLive;

    @OneToMany(mappedBy = "track", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TrackTuneTableRecord> trackTunes = new ArrayList<>();
}
