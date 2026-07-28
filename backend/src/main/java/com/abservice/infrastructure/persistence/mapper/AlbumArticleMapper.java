package com.abservice.infrastructure.persistence.mapper;

import static com.abservice.lib.Iterables.toList;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.albumarticle.AlbumAcquisitionChannel;
import com.abservice.domain.model.aggregate.albumarticle.AlbumArticle;
import com.abservice.domain.model.aggregate.albumarticle.AlbumDistribution;
import com.abservice.domain.model.vo.album.ChannelType;
import com.abservice.domain.model.vo.album.LabelTag;
import com.abservice.domain.model.vo.common.Price;
import com.abservice.domain.model.vo.common.Url;
import com.abservice.infrastructure.persistence.entity.AlbumAcquisitionChannelTableRecord;
import com.abservice.infrastructure.persistence.entity.AlbumArticleTableRecord;
import com.abservice.infrastructure.persistence.entity.AlbumDistributionTableRecord;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * AlbumArticle Mapper
 *
 * <p>
 * AlbumArticleドメインモデルとAlbumArticleTableRecordの相互変換を担当します。
 * </p>
 */
public final class AlbumArticleMapper {

    private AlbumArticleMapper() {
    }

    /**
     * EntityからDomainモデルへ変換
     *
     * @param entity
     *            AlbumArticleTableRecord
     * @return AlbumArticle
     */
    public static AlbumArticle toDomain(AlbumArticleTableRecord entity) {
        return AlbumArticle.reconstruct(
                new Album.Id(entity.getDomainId()),
                entity.getIntroLong(),
                entity.getIntroShort(),
                entity.getFirstEventSpace(),
                Optional.ofNullable(entity.getLabelTag())
                        .map(LabelTag::valueOf)
                        .orElse(null),
                toDistribution(entity.getAlbum().getAlbumDistribution()),
                toAcquisitionChannels(entity.getAlbum().getAcquisitionChannels()));
    }

    /**
     * AlbumDistributionTableRecordからAlbumDistributionへ変換
     *
     * @param entity
     *            AlbumDistributionTableRecord
     * @return AlbumDistribution（entityがnullの場合はnull）
     */
    public static @Nullable AlbumDistribution toDistribution(@Nullable AlbumDistributionTableRecord entity) {
        return Optional.ofNullable(entity)
                .map(
                        e -> AlbumDistribution.reconstruct(
                                toPrice(e.getPhysicalPrice()),
                                toPrice(e.getDownloadPrice()),
                                toUrl(e.getDemoUrl()),
                                e.getNote()))
                .orElse(null);
    }

    /**
     * AlbumDistributionからAlbumDistributionTableRecordへ変換（albumとの関連付けは呼び出し側の責務）
     *
     * @param distribution
     *            AlbumDistribution
     * @return AlbumDistributionTableRecord（distributionがnullの場合はnull）
     */
    public static @Nullable AlbumDistributionTableRecord toDistributionEntity(
            @Nullable AlbumDistribution distribution) {
        return Optional.ofNullable(distribution)
                .map(
                        d -> new AlbumDistributionTableRecord()
                                .setPhysicalPrice(toAmount(d.getPhysicalPrice()))
                                .setDownloadPrice(toAmount(d.getDownloadPrice()))
                                .setDemoUrl(toUrlValue(d.getDemoUrl()))
                                .setNote(d.getNote()))
                .orElse(null);
    }

    /**
     * AlbumAcquisitionChannelTableRecordからAlbumAcquisitionChannelへ変換
     *
     * @param entity
     *            AlbumAcquisitionChannelTableRecord
     * @return AlbumAcquisitionChannel
     */
    public static AlbumAcquisitionChannel toAcquisitionChannel(AlbumAcquisitionChannelTableRecord entity) {
        return AlbumAcquisitionChannel.reconstruct(
                AlbumAcquisitionChannel.Id.of(entity.getDomainId()),
                ChannelType.valueOf(entity.getChannelType()),
                entity.getName(),
                toUrl(entity.getUrl()),
                entity.getNote());
    }

    /**
     * AlbumAcquisitionChannelTableRecordのリストからAlbumAcquisitionChannelのリストへ変換
     *
     * @param entities
     *            AlbumAcquisitionChannelTableRecordのリスト
     * @return AlbumAcquisitionChannelのリスト（entitiesがnullの場合は空リスト）
     */
    public static List<AlbumAcquisitionChannel> toAcquisitionChannels(
            @Nullable List<AlbumAcquisitionChannelTableRecord> entities) {
        return Optional.ofNullable(entities)
                .map(toList(AlbumArticleMapper::toAcquisitionChannel))
                .orElseGet(List::of);
    }

    /**
     * AlbumAcquisitionChannelからAlbumAcquisitionChannelTableRecordへ変換（albumとの関連付けは呼び出し側の責務）
     *
     * @param channel
     *            AlbumAcquisitionChannel
     * @return AlbumAcquisitionChannelTableRecord
     */
    public static AlbumAcquisitionChannelTableRecord toAcquisitionChannelEntity(AlbumAcquisitionChannel channel) {
        return new AlbumAcquisitionChannelTableRecord()
                .setDomainId(channel.id().value())
                .setChannelType(channel.getChannelType().name())
                .setName(channel.getName())
                .setUrl(toUrlValue(channel.getUrl()))
                .setNote(channel.getNote());
    }

    private static @Nullable Price toPrice(@Nullable Integer amount) {
        return Optional.ofNullable(amount)
                .map(Price::of)
                .orElse(null);
    }

    private static @Nullable Integer toAmount(@Nullable Price price) {
        return Optional.ofNullable(price)
                .map(Price::amount)
                .orElse(null);
    }

    private static @Nullable Url toUrl(@Nullable String value) {
        return Optional.ofNullable(value)
                .map(Url::of)
                .orElse(null);
    }

    private static @Nullable String toUrlValue(@Nullable Url url) {
        return Optional.ofNullable(url)
                .map(Url::value)
                .orElse(null);
    }

    /**
     * DomainモデルからEntityへ変換
     *
     * @param albumArticle
     *            AlbumArticle
     * @return AlbumArticleTableRecord
     */
    public static AlbumArticleTableRecord toEntity(AlbumArticle albumArticle) {
        return new AlbumArticleTableRecord()
                .setDomainId(albumArticle.albumId().value())
                .setIntroLong(albumArticle.introLong())
                .setIntroShort(albumArticle.introShort())
                .setFirstEventSpace(albumArticle.firstEventSpace())
                .setLabelTag(
                        Optional.ofNullable(albumArticle.labelTag())
                                .map(LabelTag::name)
                                .orElse(null));
    }
}
