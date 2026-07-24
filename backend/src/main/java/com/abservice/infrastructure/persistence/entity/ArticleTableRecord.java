package com.abservice.infrastructure.persistence.entity;

import com.abservice.infrastructure.persistence.AuditableTableRecord;
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
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 記事テーブルレコード
 * <p>
 * テーブル: article
 * </p>
 */
@Entity
@Table(name = "article")
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ArticleTableRecord extends AuditableTableRecord<ArticleTableRecord> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "article_id")
    private Long articleId;

    @Column(name = "domain_id", nullable = false, unique = true)
    private String domainId;

    @Column(name = "article_type", nullable = false, length = 50)
    private String articleType;

    @Column(name = "album_id")
    private String albumId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "body_format", nullable = false, length = 20)
    private String bodyFormat = "PLAIN_TEXT";

    @Column(name = "intro_short", columnDefinition = "TEXT")
    private String introShort;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "updated_at_business")
    private Instant updatedAtBusiness;

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = false;

    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ArticleTagLinkTableRecord> articleTagLinks = new ArrayList<>();
}
