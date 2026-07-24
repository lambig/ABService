package com.abservice.infrastructure.persistence.entity;

import com.abservice.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * アルバム記事・お品書き用メタ情報テーブルレコード
 * <p>
 * テーブル: album_article
 * </p>
 */
@Entity
@Table(name = "album_article")
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class AlbumArticleTableRecord extends AuditableEntity {

    @Id
    @Column(name = "album_id")
    private Long albumId;

    @Column(name = "domain_id", nullable = false, unique = true)
    private String domainId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "album_id")
    private AlbumTableRecord album;

    @Column(name = "intro_long", columnDefinition = "TEXT")
    private String introLong;

    @Column(name = "intro_short", columnDefinition = "TEXT")
    private String introShort;

    @Column(name = "first_event_space", length = 100)
    private String firstEventSpace;

    @Column(name = "label_tag", length = 50)
    private String labelTag;
}
