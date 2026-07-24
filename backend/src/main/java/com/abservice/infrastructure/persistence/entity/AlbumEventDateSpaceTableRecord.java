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
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * アルバムイベント日付・スペーステーブルレコード
 * <p>
 * テーブル: album_event_date_space
 * </p>
 * <p>
 * 複数日程参加に対応するため、イベントの日付とスペース番号の組み合わせを管理します。
 * </p>
 */
@Entity
@Table(name = "album_event_date_space")
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class AlbumEventDateSpaceTableRecord extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "album_event_date_space_id")
    private Long albumEventDateSpaceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id", nullable = false)
    private AlbumTableRecord album;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "space_number", length = 50)
    private String spaceNumber;
}
