package com.abservice.infrastructure.persistence.entity;

import com.abservice.infrastructure.persistence.AuditableTableRecord;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * トラック内のチューン構成テーブルレコード
 * <p>
 * テーブル: track_tune（中間テーブル）
 * </p>
 */
@Entity
@Table(name = "track_tune")
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class TrackTuneTableRecord extends AuditableTableRecord<TrackTuneTableRecord> {

    @EmbeddedId
    private TrackTuneId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("trackId")
    @JoinColumn(name = "track_id", nullable = false)
    private TrackTableRecord track;

    @Column(name = "tune_id")
    private String tuneId;

    @Column(name = "tune_title", length = 255)
    private String tuneTitle;

    @Column(name = "composer_credit_override", length = 255)
    private String composerCreditOverride;

    @Column(name = "arranger_credit_override", length = 255)
    private String arrangerCreditOverride;

    @Column(name = "link_url", length = 500)
    private String linkUrl;
}
