package com.abservice.domain.model.aggregate.albumarticle;

import com.abservice.domain.model.vo.common.Price;
import com.abservice.domain.model.vo.common.Url;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.With;

/**
 * アルバム頒布情報（集約内エンティティ）
 *
 * <p>
 * 頒価、DL価格、デモリンクなど、作品側の頒布状態を管理します。
 * </p>
 */
@With(AccessLevel.PRIVATE)
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AlbumDistribution {
    private final Price physicalPrice; // nullable: CD等の物理頒価
    private final Price downloadPrice; // nullable: DL版価格
    private final Url demoUrl; // nullable: デモ音源へのリンク
    private final String note; // nullable: 補足メモ

    /**
     * 新規AlbumDistributionを生成
     *
     * @param physicalPrice
     *            物理頒価（nullable）
     * @param downloadPrice
     *            DL価格（nullable）
     * @param demoUrl
     *            デモURL（nullable）
     * @param note
     *            補足メモ（nullable）
     * @return 新規AlbumDistribution
     */
    public static AlbumDistribution create(Price physicalPrice, Price downloadPrice, Url demoUrl, String note) {
        return new AlbumDistribution(physicalPrice, downloadPrice, demoUrl, note);
    }

    /**
     * 永続化層からの再構成
     *
     * @param physicalPrice
     *            物理頒価（nullable）
     * @param downloadPrice
     *            DL価格（nullable）
     * @param demoUrl
     *            デモURL（nullable）
     * @param note
     *            補足メモ（nullable）
     * @return 再構成されたAlbumDistribution
     */
    public static AlbumDistribution reconstruct(Price physicalPrice, Price downloadPrice, Url demoUrl, String note) {
        return new AlbumDistribution(physicalPrice, downloadPrice, demoUrl, note);
    }

    /**
     * 物理頒価を変更
     *
     * @param newPhysicalPrice
     *            新しい物理頒価
     * @return 更新されたAlbumDistribution
     */
    public AlbumDistribution changePhysicalPrice(Price newPhysicalPrice) {
        return withPhysicalPrice(newPhysicalPrice);
    }

    /**
     * DL価格を変更
     *
     * @param newDownloadPrice
     *            新しいDL価格
     * @return 更新されたAlbumDistribution
     */
    public AlbumDistribution changeDownloadPrice(Price newDownloadPrice) {
        return withDownloadPrice(newDownloadPrice);
    }

    /**
     * デモURLを変更
     *
     * @param newDemoUrl
     *            新しいデモURL
     * @return 更新されたAlbumDistribution
     */
    public AlbumDistribution changeDemoUrl(Url newDemoUrl) {
        return withDemoUrl(newDemoUrl);
    }

    /**
     * 補足メモを変更
     *
     * @param newNote
     *            新しい補足メモ
     * @return 更新されたAlbumDistribution
     */
    public AlbumDistribution changeNote(String newNote) {
        return withNote(newNote);
    }
}
