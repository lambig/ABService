package com.abservice.infrastructure.persistence.mapper;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.albumarticle.AlbumAcquisitionChannel;
import com.abservice.domain.model.aggregate.albumarticle.AlbumArticle;
import com.abservice.domain.model.aggregate.albumarticle.AlbumDistribution;
import com.abservice.domain.model.vo.album.ChannelType;
import com.abservice.domain.model.vo.album.LabelTag;
import com.abservice.domain.model.vo.common.Price;
import com.abservice.domain.model.vo.common.Url;
import com.abservice.infrastructure.persistence.entity.AlbumAcquisitionChannelEntity;
import com.abservice.infrastructure.persistence.entity.AlbumArticleEntity;
import com.abservice.infrastructure.persistence.entity.AlbumDistributionEntity;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * AlbumArticle Mapper
 *
 * <p>
 * AlbumArticleドメインモデルとAlbumArticleEntityの相互変換を担当します。
 * </p>
 */
public final class AlbumArticleMapper {

    private AlbumArticleMapper() {
        // ユーティリティクラス
    }

    /**
     * EntityからDomainモデルへ変換
     *
     * @param entity
     *            AlbumArticleEntity
     * @return AlbumArticle
     */
    public static AlbumArticle toDomain(AlbumArticleEntity entity) {
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
     * AlbumDistributionEntityからAlbumDistributionへ変換
     *
     * @param entity
     *            AlbumDistributionEntity
     * @return AlbumDistribution（entityがnullの場合はnull）
     */
    public static @Nullable AlbumDistribution toDistribution(@Nullable AlbumDistributionEntity entity) {
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
     * AlbumDistributionからAlbumDistributionEntityへ変換（albumとの関連付けは呼び出し側の責務）
     *
     * @param distribution
     *            AlbumDistribution
     * @return AlbumDistributionEntity（distributionがnullの場合はnull）
     */
    public static @Nullable AlbumDistributionEntity toDistributionEntity(@Nullable AlbumDistribution distribution) {
        return Optional.ofNullable(distribution)
                .map(d -> {
                    final var entity = new AlbumDistributionEntity();
                    entity.setPhysicalPrice(toAmount(d.getPhysicalPrice()));
                    entity.setDownloadPrice(toAmount(d.getDownloadPrice()));
                    entity.setDemoUrl(toUrlValue(d.getDemoUrl()));
                    entity.setNote(d.getNote());
                    return entity;
                })
                .orElse(null);
    }

    /**
     * AlbumAcquisitionChannelEntityからAlbumAcquisitionChannelへ変換
     *
     * @param entity
     *            AlbumAcquisitionChannelEntity
     * @return AlbumAcquisitionChannel
     */
    public static AlbumAcquisitionChannel toAcquisitionChannel(AlbumAcquisitionChannelEntity entity) {
        return AlbumAcquisitionChannel.reconstruct(
                AlbumAcquisitionChannel.Id.of(entity.getDomainId()),
                ChannelType.valueOf(entity.getChannelType()),
                entity.getName(),
                toUrl(entity.getUrl()),
                entity.getNote());
    }

    /**
     * AlbumAcquisitionChannelEntityのリストからAlbumAcquisitionChannelのリストへ変換
     *
     * @param entities
     *            AlbumAcquisitionChannelEntityのリスト
     * @return AlbumAcquisitionChannelのリスト（entitiesがnullの場合は空リスト）
     */
    public static List<AlbumAcquisitionChannel> toAcquisitionChannels(
            @Nullable List<AlbumAcquisitionChannelEntity> entities) {
        return Optional.ofNullable(entities)
                .map(list -> list.stream().map(AlbumArticleMapper::toAcquisitionChannel).toList())
                .orElseGet(List::of);
    }

    /**
     * AlbumAcquisitionChannelからAlbumAcquisitionChannelEntityへ変換（albumとの関連付けは呼び出し側の責務）
     *
     * @param channel
     *            AlbumAcquisitionChannel
     * @return AlbumAcquisitionChannelEntity
     */
    public static AlbumAcquisitionChannelEntity toAcquisitionChannelEntity(AlbumAcquisitionChannel channel) {
        final var entity = new AlbumAcquisitionChannelEntity();
        entity.setDomainId(channel.id().value());
        entity.setChannelType(channel.getChannelType().name());
        entity.setName(channel.getName());
        entity.setUrl(toUrlValue(channel.getUrl()));
        entity.setNote(channel.getNote());
        return entity;
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
     * @return AlbumArticleEntity
     */
    public static AlbumArticleEntity toEntity(AlbumArticle albumArticle) {
        final var albumArticleEntity = new AlbumArticleEntity();
        albumArticleEntity.setDomainId(albumArticle.albumId().value());
        albumArticleEntity.setIntroLong(albumArticle.introLong());
        albumArticleEntity.setIntroShort(albumArticle.introShort());
        albumArticleEntity.setFirstEventSpace(albumArticle.firstEventSpace());
        albumArticleEntity.setLabelTag(
                Optional.ofNullable(albumArticle.labelTag())
                        .map(LabelTag::name)
                        .orElse(null));

        return albumArticleEntity;
    }
}
