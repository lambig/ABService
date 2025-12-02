package com.abservice.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * ArticleTagLinkエンティティの複合主キー
 * <p>
 * テーブル: article_tag_link
 * </p>
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ArticleTagLinkId implements Serializable {

    @Column(name = "article_id")
    private Long articleId;

    @Column(name = "article_tag_id")
    private Long articleTagId;
}
