package com.abservice.infrastructure.persistence.entity;

import com.abservice.infrastructure.persistence.AuditableTableRecord;
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
import lombok.experimental.Accessors;

/**
 * アルバムの外部音源テーブルレコード
 * <p>
 * テーブル: album_external_audio
 * </p>
 */
@Entity
@Table(name = "album_external_audio")
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class AlbumExternalAudioTableRecord extends AuditableTableRecord<AlbumExternalAudioTableRecord> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "album_external_audio_id")
    private Long albumExternalAudioId;

    @Column(name = "domain_id", nullable = false, unique = true)
    private String domainId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id", nullable = false)
    private AlbumTableRecord album;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "url", nullable = false, length = 500)
    private String url;
}
