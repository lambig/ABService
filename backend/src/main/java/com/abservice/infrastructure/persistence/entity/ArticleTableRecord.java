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
import jakarta.persistence.OneToOne;
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

    /**
     * アルバムへの参照
     * <p>
     * ALBUM種別の記事だけが持つ。参照を持たない記事はnull。
     * </p>
     * <p>
     * 単一値のため EAGER で取得する。ページング付きの一覧照会は JQL を組み立てず Panache の {@code findAll}
     * 経由で発行されるため、{@code JOIN FETCH} を指定する場所がない。読み取り側の
     * 速度はキャッシュ・全文検索など別の層で作る方針のため、ここでは常に取得する形を採る。
     * </p>
     */
    @OneToOne(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private ArticleAlbumReferenceTableRecord albumReference;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body = "";

    @Column(name = "body_format", nullable = false, length = 20)
    private String bodyFormat = "PLAIN_TEXT";

    @Column(name = "intro_short", nullable = false, length = 120)
    private String introShort = "";

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "updated_at_business")
    private Instant updatedAtBusiness;

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = false;

    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ArticleTagLinkTableRecord> articleTagLinks = new ArrayList<>();
}
