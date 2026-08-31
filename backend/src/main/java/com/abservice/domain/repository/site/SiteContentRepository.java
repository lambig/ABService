package com.abservice.domain.repository.site;

import com.abservice.domain.model.aggregate.site.SiteContent;
import com.abservice.domain.model.vo.site.SiteContentKey;
import com.abservice.domain.repository.Repository;
import io.smallrye.mutiny.Uni;

/**
 * サイト文言リポジトリ
 *
 * <p>
 * SiteContent集約の永続化と取得を担当します。
 * </p>
 *
 * <p>
 * 取得はキーで行います。ドメインIDも持ちますが（オブジェクトレジストリ #174 との整合のため）、文言を引く ときに使うのはキーであり、ID
 * を外部へ出す経路は持ちません。
 * </p>
 */
public interface SiteContentRepository extends Repository<SiteContent, SiteContent.Id> {

    /**
     * キーでサイト文言を取得
     *
     * @param key
     *            どの文言かを指すキー
     * @return 該当するサイト文言（未存在の場合はnull）
     */
    Uni<SiteContent> findByKey(SiteContentKey key);
}
