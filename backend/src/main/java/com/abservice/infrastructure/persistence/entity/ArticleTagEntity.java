package com.abservice.infrastructure.persistence.entity;

import com.abservice.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
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

    @Column(name = "domain_id", nullable = false, unique = true)
    private String domainId;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @OneToMany(mappedBy = "articleTag", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ArticleTagLinkEntity> articleTagLinks = new ArrayList<>();
}
