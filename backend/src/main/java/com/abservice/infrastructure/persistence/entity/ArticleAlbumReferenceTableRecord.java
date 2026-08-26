package com.abservice.infrastructure.persistence.entity;

import com.abservice.infrastructure.persistence.AuditableTableRecord;
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

import java.time.Instant;

/**
 * 記事のアルバム参照テーブルレコード
 * <p>
 * テーブル: article_album_reference
 * </p>
 * <p>
 * アルバムを参照できるのは ALBUM 種別の記事だけのため、本体テーブルから分けている。参照を持たない記事は行を持たない。
 * </p>
 */
@Entity
@Table(name = "article_album_reference")
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ArticleAlbumReferenceTableRecord extends AuditableTableRecord<ArticleAlbumReferenceTableRecord> {

    @Id
    @Column(name = "article_id")
    private Long articleId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "article_id")
    private ArticleTableRecord article;

    @Column(name = "album_id")
    private String albumId;

    @Column(name = "former_album_id")
    private String formerAlbumId;

    @Column(name = "album_reference_lost_at")
    private Instant albumReferenceLostAt;

    @Column(name = "album_reference_lost_reason", length = 50)
    private String albumReferenceLostReason;
}
