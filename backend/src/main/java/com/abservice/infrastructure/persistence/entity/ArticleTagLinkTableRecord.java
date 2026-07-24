package com.abservice.infrastructure.persistence.entity;

import com.abservice.infrastructure.persistence.AuditableTableRecord;
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
 * 記事タグリンクテーブルレコード
 * <p>
 * テーブル: article_tag_link（中間テーブル）
 * </p>
 */
@Entity
@Table(name = "article_tag_link")
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ArticleTagLinkTableRecord extends AuditableTableRecord<ArticleTagLinkTableRecord> {

    @EmbeddedId
    private ArticleTagLinkId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("articleId")
    @JoinColumn(name = "article_id", nullable = false)
    private ArticleTableRecord article;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("articleTagId")
    @JoinColumn(name = "article_tag_id", nullable = false)
    private ArticleTagTableRecord articleTag;
}
