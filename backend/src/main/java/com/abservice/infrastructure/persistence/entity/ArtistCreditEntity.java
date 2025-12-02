package com.abservice.infrastructure.persistence.entity;

import com.abservice.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * アーティスト名義エンティティ
 * <p>
 * テーブル: artist_credit
 * </p>
 */
@Entity
@Table(name = "artist_credit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ArtistCreditEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "artist_credit_id")
    private Long artistCreditId;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "sort_key", length = 255)
    private String sortKey;
}
