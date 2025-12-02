package com.abservice.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * TrackTuneエンティティの複合主キー
 * <p>
 * テーブル: track_tune
 * </p>
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TrackTuneId implements Serializable {

    @Column(name = "track_id")
    private Long trackId;

    @Column(name = "seq")
    private Integer seq;
}
