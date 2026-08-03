package com.abservice.infrastructure.persistence.entity;

import com.abservice.infrastructure.persistence.AuditableTableRecord;
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
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * アルバムテーブルレコード
 * <p>
 * テーブル: album
 * </p>
 */
@Entity
@Table(name = "album")
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class AlbumTableRecord extends AuditableTableRecord<AlbumTableRecord> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "album_id")
    private Long albumId;

    @Column(name = "domain_id", nullable = false, unique = true)
    private String domainId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "artist_display_name", nullable = false, length = 255)
    private String artistDisplayName;

    @Column(name = "artist_sort_key", length = 255)
    private String artistSortKey;

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

    @Column(name = "catalog_number", length = 100)
    private String catalogNumber;

    @Column(name = "isdn", length = 20)
    private String isdn;

    @OneToMany(mappedBy = "album", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TrackTableRecord> tracks = new ArrayList<>();

    @OneToOne(mappedBy = "album", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private AlbumArticleTableRecord albumArticle;

    @OneToOne(mappedBy = "album", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private AlbumDistributionTableRecord albumDistribution;

    @OneToMany(mappedBy = "album", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AlbumAcquisitionChannelTableRecord> acquisitionChannels = new ArrayList<>();

    /**
     * トラックを差し替える（managed collection を in-place で置換し、親参照を張り直す）。
     *
     * <p>
     * orphanRemoval 下では同一コレクションインスタンスの変異が必須のため、リスト参照の 再代入ではなく clear + 再追加で行う。呼び出し側が
     * {@code getTracks().clear()/add()} を直接触らずに済むよう、エンティティ自身がこの不変条件を担う。
     * </p>
     *
     * @param newTracks
     *            新しいトラック
     * @return このエンティティ（chainable）
     */
    public AlbumTableRecord replaceTracks(List<TrackTableRecord> newTracks) {
        this.tracks.clear();
        newTracks.forEach(track -> {
            track.setAlbum(this);
            this.tracks.add(track);
        });
        return this;
    }
}
