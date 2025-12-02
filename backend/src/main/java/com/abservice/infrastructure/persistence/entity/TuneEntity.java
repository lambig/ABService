package com.abservice.infrastructure.persistence.entity;

import com.abservice.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * チューン（曲）エンティティ
 * <p>
 * テーブル: tune
 * </p>
 */
@Entity
@Table(name = "tune")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TuneEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tune_id")
    private Long tuneId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "tune_kind", nullable = false, length = 20)
    private String tuneKind;

    @Column(name = "default_composer_credit", length = 255)
    private String defaultComposerCredit;

    @Column(name = "default_arranger_credit", length = 255)
    private String defaultArrangerCredit;

    @Column(name = "original_work_title", length = 255)
    private String originalWorkTitle;

    @Column(name = "original_work_credit", length = 255)
    private String originalWorkCredit;

    @Column(name = "tune_type", length = 50)
    private String tuneType;

    @Column(name = "default_key", length = 20)
    private String defaultKey;

    @Column(name = "default_tempo")
    private Integer defaultTempo;
}
