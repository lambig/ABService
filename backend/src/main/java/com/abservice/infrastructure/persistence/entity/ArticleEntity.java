package com.abservice.infrastructure.persistence.entity;

import com.abservice.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 記事エンティティ
 * <p>
 * テーブル: article
 * </p>
 */
@Entity
@Table(name = "article")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ArticleEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "article_id")
    private Long articleId;

    @Column(name = "article_type", nullable = false, length = 50)
    private String articleType;

    @Column(name = "album_id")
    private Long albumId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "intro_short", columnDefinition = "TEXT")
    private String introShort;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "updated_at_business")
    private Instant updatedAtBusiness;

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = false;

    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ArticleTagLinkEntity> articleTagLinks = new ArrayList<>();
}
