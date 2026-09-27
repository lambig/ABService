package com.abservice.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 選択中クロスフェードの読取用レコード。世代条件付きSQLで更新する。 */
@Entity
@Table(name = "album_crossfade")
@Getter
@NoArgsConstructor
public class AlbumCrossfadeTableRecord {
    @Id
    @Column(name = "album_id")
    private Long albumId;
    @Column(name = "audio_id", nullable = false)
    private UUID audioId;
    @Column(name = "revision", nullable = false)
    private int revision;
}
