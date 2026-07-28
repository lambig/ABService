package com.abservice.domain.model.aggregate.albumarticle;

import com.abservice.domain.model.AggregateFactory;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.common.Price;
import com.abservice.domain.model.vo.common.Url;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

/**
 * アルバム頒布情報（集約内エンティティ）
 *
 * <p>
 * 頒価、DL価格、デモリンクなど、作品側の頒布状態を管理します。
 * </p>
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class AlbumDistribution {
    /** 物理頒価 */
    @Nullable
    private final Price physicalPrice;
    /** DL版価格 */
    @Nullable
    private final Price downloadPrice;
    /** デモ音源へのリンク */
    @Nullable
    private final Url demoUrl;
    /** 補足メモ */
    @Nullable
    private final String note;

    private AlbumDistribution(@Nullable Price physicalPrice, @Nullable Price downloadPrice, @Nullable Url demoUrl,
            @Nullable String note) {
        this.physicalPrice = physicalPrice;
        this.downloadPrice = downloadPrice;
        this.demoUrl = demoUrl;
        this.note = note;
    }

    // 検証対象フィールドが現状ないためPolicy.<Stub>all()はルール0件（常に成功）だが、
    // 他クラスと同じ形に揃えることで一目で正しさを判定できるようにする。
    private static AlbumDistribution factory(@Nullable Price physicalPrice, @Nullable Price downloadPrice,
            @Nullable Url demoUrl, @Nullable String note) {
        return Policy.<Stub>all()
                .verify(
                        new Stub(
                                physicalPrice,
                                downloadPrice,
                                demoUrl,
                                note),
                        Stub::asAlbumDistribution)
                .resolve(Policy::illegalArgument);
    }

    @NullUnmarked
    private record Stub(Price physicalPrice, Price downloadPrice, Url demoUrl, String note) {

        @AggregateFactory
        @NonNull
        AlbumDistribution asAlbumDistribution() {
            return new AlbumDistribution(
                    physicalPrice(),
                    downloadPrice(),
                    demoUrl(),
                    note());
        }
    }

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
    public static AlbumDistribution create(
            @Nullable Price physicalPrice,
            @Nullable Price downloadPrice,
            @Nullable Url demoUrl,
            @Nullable String note) {
        return AlbumDistribution.factory(
                physicalPrice,
                downloadPrice,
                demoUrl,
                note);
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
    public static AlbumDistribution reconstruct(
            @Nullable Price physicalPrice,
            @Nullable Price downloadPrice,
            @Nullable Url demoUrl,
            @Nullable String note) {
        return AlbumDistribution.factory(
                physicalPrice,
                downloadPrice,
                demoUrl,
                note);
    }

    /**
     * 物理頒価を変更
     *
     * @param newPhysicalPrice
     *            新しい物理頒価
     * @return 更新されたAlbumDistribution
     */
    public AlbumDistribution changePhysicalPrice(Price newPhysicalPrice) {
        return AlbumDistribution.factory(
                newPhysicalPrice,
                downloadPrice,
                demoUrl,
                note);
    }

    /**
     * DL価格を変更
     *
     * @param newDownloadPrice
     *            新しいDL価格
     * @return 更新されたAlbumDistribution
     */
    public AlbumDistribution changeDownloadPrice(Price newDownloadPrice) {
        return AlbumDistribution.factory(
                physicalPrice,
                newDownloadPrice,
                demoUrl,
                note);
    }

    /**
     * デモURLを変更
     *
     * @param newDemoUrl
     *            新しいデモURL
     * @return 更新されたAlbumDistribution
     */
    public AlbumDistribution changeDemoUrl(Url newDemoUrl) {
        return AlbumDistribution.factory(
                physicalPrice,
                downloadPrice,
                newDemoUrl,
                note);
    }

    /**
     * 補足メモを変更
     *
     * @param newNote
     *            新しい補足メモ
     * @return 更新されたAlbumDistribution
     */
    public AlbumDistribution changeNote(String newNote) {
        return AlbumDistribution.factory(
                physicalPrice,
                downloadPrice,
                demoUrl,
                newNote);
    }
}
