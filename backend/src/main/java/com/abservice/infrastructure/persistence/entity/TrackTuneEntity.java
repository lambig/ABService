package com.abservice.infrastructure.persistence.entity;

import com.abservice.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * トラック内のチューン構成エンティティ
 * <p>
 * テーブル: track_tune（中間テーブル）
 * </p>
 */
@Entity
@Table(name = "track_tune")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrackTuneEntity extends AuditableEntity {

    @EmbeddedId
    private TrackTuneId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("trackId")
    @JoinColumn(name = "track_id", nullable = false)
    private TrackEntity track;

    @Column(name = "tune_id")
    private Long tuneId;

    @Column(name = "composer_credit_override", length = 255)
    private String composerCreditOverride;

    @Column(name = "arranger_credit_override", length = 255)
    private String arrangerCreditOverride;

    @Column(name = "link_url", length = 500)
    private String linkUrl;
}
