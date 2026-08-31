package com.abservice.infrastructure.persistence.entity;

import com.abservice.infrastructure.persistence.AuditableTableRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * サイト文言テーブルレコード
 * <p>
 * テーブル: site_content
 * </p>
 */
@Entity
@Table(name = "site_content")
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class SiteContentTableRecord extends AuditableTableRecord<SiteContentTableRecord> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "site_content_id")
    private Long siteContentId;

    @Column(name = "domain_id", nullable = false, unique = true)
    private String domainId;

    @Column(name = "content_key", nullable = false, unique = true, length = 100)
    private String contentKey;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "content_format", nullable = false, length = 20)
    private String contentFormat = "PLAIN_TEXT";
}
