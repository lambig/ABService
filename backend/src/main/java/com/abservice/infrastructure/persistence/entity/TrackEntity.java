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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * トラック（録音単位）エンティティ
 * <p>
 * テーブル: track
 * </p>
 */
@Entity
@Table(name = "track")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrackEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "track_id")
    private Long trackId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id", nullable = false)
    private AlbumEntity album;

    @Column(name = "track_no", nullable = false)
    private Integer trackNo;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "artist_credit_id")
    private Long artistCreditId;

    @Column(name = "recording_date")
    private LocalDate recordingDate;

    @Column(name = "recording_place", length = 255)
    private String recordingPlace;

    @Column(name = "duration_msec")
    private Integer durationMsec;

    @Column(name = "is_live")
    private Boolean isLive;

    @Column(name = "isrc", length = 20)
    private String isrc;

    @OneToMany(mappedBy = "track", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TrackTuneEntity> trackTunes = new ArrayList<>();
}
