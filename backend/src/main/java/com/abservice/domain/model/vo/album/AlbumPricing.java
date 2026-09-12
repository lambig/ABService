package com.abservice.domain.model.vo.album;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

/**
 * 作品の頒布額の値オブジェクト
 *
 * <p>
 * 作品は既定額を1つ持ち、経路ごとの額はそこからの上書きとして持ちます。上書きが無い経路では既定額が
 * その経路の額になります。経路ごとに額を必ず持たせる形にしないのは、多くの作品でどの経路も同じ額に なり、同じ値を三度入れることになるためです。
 * </p>
 *
 * <p>
 * 既定額は必須です。「額が決まっていない」状態は、この値オブジェクトを持たないこと（作品側の項目が null
 * であること）で表します。額の一部だけが決まっている状態を持たせると、読み手にいくらなのかを 答えられない額が画面へ出ます。
 * </p>
 *
 * @param standard
 *            既定額
 * @param venue
 *            会場での額（nullable。null は既定額）
 * @param consignment
 *            委託での額（nullable。null は既定額）
 * @param download
 *            ダウンロードでの額（nullable。null は既定額）
 */
public record AlbumPricing(
        Price standard,
        @Nullable Price venue,
        @Nullable Price consignment,
        @Nullable Price download) implements ValueObject<AlbumPricing> {

    /** 既定額必須違反時のエラー */
    private static final ErrorResult STANDARD_REQUIRED_ERROR = new ErrorResult(
            "standard",
            "Standard price cannot be null",
            "ALBUM_PRICING_STANDARD_REQUIRED");

    /**
     * コンストラクタ
     *
     * @param standard
     *            既定額
     * @param venue
     *            会場での額（nullable）
     * @param consignment
     *            委託での額（nullable）
     * @param download
     *            ダウンロードでの額（nullable）
     * @throws IllegalArgumentException
     *             既定額がnullの場合
     */
    public AlbumPricing {
        Policy.<Price>of(
                Objects::nonNull,
                STANDARD_REQUIRED_ERROR)
                .verify(standard, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    /**
     * 経路ごとの上書きを持たない頒布額を生成します。
     *
     * @param standard
     *            既定額
     * @return AlbumPricingインスタンス
     */
    public static AlbumPricing of(Price standard) {
        return new AlbumPricing(
                standard,
                null,
                null,
                null);
    }

    /**
     * その経路で手に取るときの額
     *
     * <p>
     * 上書きがあればその額、無ければ既定額を返します。
     * </p>
     *
     * @param channel
     *            頒布の経路
     * @return その経路の額
     */
    public Price at(DistributionChannel channel) {
        return Optional.ofNullable(
                switch (channel) {
                    case VENUE -> venue;
                    case CONSIGNMENT -> consignment;
                    case DOWNLOAD -> download;
                })
                .orElse(standard);
    }

    /**
     * その経路が既定額と違う額を持つか
     *
     * @param channel
     *            頒布の経路
     * @return 上書きを持つならtrue
     */
    public boolean overrides(DistributionChannel channel) {
        return switch (channel) {
            case VENUE -> venue != null;
            case CONSIGNMENT -> consignment != null;
            case DOWNLOAD -> download != null;
        };
    }

    @Override
    public boolean equivalentTo(AlbumPricing other) {
        return Optional.ofNullable(other)
                .filter(o -> Objects.equals(this.standard, o.standard))
                .filter(o -> Objects.equals(this.venue, o.venue))
                .filter(o -> Objects.equals(this.consignment, o.consignment))
                .filter(o -> Objects.equals(this.download, o.download))
                .isPresent();
    }
}
