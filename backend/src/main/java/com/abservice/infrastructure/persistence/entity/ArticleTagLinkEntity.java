package com.abservice.infrastructure.persistence.entity;

import com.abservice.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 記事タグリンクエンティティ
 * <p>
 * テーブル: article_tag_link（中間テーブル）
 * </p>
 */
@Entity
@Table(name = "article_tag_link")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ArticleTagLinkEntity extends AuditableEntity {

    @EmbeddedId
    private ArticleTagLinkId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("articleId")
    @JoinColumn(name = "article_id", nullable = false)
    private ArticleEntity article;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("articleTagId")
    @JoinColumn(name = "article_tag_id", nullable = false)
    private ArticleTagEntity articleTag;
}
