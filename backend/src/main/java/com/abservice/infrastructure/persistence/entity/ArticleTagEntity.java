package com.abservice.infrastructure.persistence.entity;

import com.abservice.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 記事タグエンティティ
 * <p>
 * テーブル: article_tag
 * </p>
 */
@Entity
@Table(name = "article_tag")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ArticleTagEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "article_tag_id")
    private Long articleTagId;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @OneToMany(mappedBy = "articleTag", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ArticleTagLinkEntity> articleTagLinks = new ArrayList<>();
}
