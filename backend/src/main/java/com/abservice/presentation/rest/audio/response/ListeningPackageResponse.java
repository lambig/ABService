package com.abservice.presentation.rest.audio.response;

import static com.abservice.lib.Iterables.toList;

import com.abservice.application.query.audio.ListeningPackageView;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 端末へ渡す Manifest v2（{@code packages/installation} の schema v2 と同じ項目だけを持つ）。
 *
 * <p>
 * {@link ListeningPackageView} を1対1で写す。値をここで足したり変えたりすると packageVersion
 * が内容を表さなくなるため、 項目の確定は View 側で行う。項目を足すと端末側の厳密な検証が拒否するため、schema と一致する形に保つ。
 * URL・保存キー・秘密は含まない。
 * </p>
 *
 * @param schemaVersion
 *            Manifest の schema 版
 * @param packageVersion
 *            内容の digest。同じ内容なら同じ値
 * @param compatibleAppVersion
 *            実行できるアプリの版の範囲
 * @param presentationAssetIds
 *            必須の表示素材の assetId
 * @param assets
 *            取得対象の実体
 * @param albums
 *            収録曲を含む作品の説明情報
 * @param playbackItems
 *            選択して再生できる項目（表示順）
 */
public record ListeningPackageResponse(
        int schemaVersion,
        String packageVersion,
        ListeningAppVersionRangeResponse compatibleAppVersion,
        List<String> presentationAssetIds,
        List<ListeningAssetResponse> assets,
        List<ListeningAlbumResponse> albums,
        List<ListeningPlaybackItemResponse> playbackItems) {

    public static ListeningPackageResponse of(ListeningPackageView view) {
        return new ListeningPackageResponse(
                view.schemaVersion(),
                view.packageVersion(),
                ListeningAppVersionRangeResponse.of(view.compatibleAppVersion()),
                view.presentationAssetIds(),
                toList(ListeningAssetResponse::of)
                        .apply(view.assets()),
                toList(ListeningAlbumResponse::of)
                        .apply(view.albums()),
                toList(ListeningPlaybackItemResponse::of)
                        .apply(view.playbackItems()));
    }

    /**
     * @param minInclusive
     *            この版以上
     * @param maxExclusive
     *            この版未満
     */
    public record ListeningAppVersionRangeResponse(List<Integer> minInclusive, List<Integer> maxExclusive) {
        private static ListeningAppVersionRangeResponse of(ListeningPackageView.AppVersionRange range) {
            return new ListeningAppVersionRangeResponse(range.minInclusive(), range.maxExclusive());
        }
    }

    /**
     * @param assetId
     *            登録ID。取得URLではない
     * @param mediaType
     *            音源は {@code audio/flac}、表示素材は画像の Content-Type（{@code image/png} など）
     * @param byteLength
     *            実測バイト数
     * @param checksum
     *            SHA-256
     * @param required
     *            準備完了に必須なら真
     */
    public record ListeningAssetResponse(
            String assetId,
            String mediaType,
            long byteLength,
            ListeningChecksumResponse checksum,
            boolean required) {
        private static ListeningAssetResponse of(ListeningPackageView.Asset asset) {
            return new ListeningAssetResponse(
                    asset.assetId(),
                    asset.mediaType(),
                    asset.byteLength(),
                    ListeningChecksumResponse.of(asset.checksum()),
                    asset.required());
        }
    }

    /**
     * @param algorithm
     *            {@code sha256}
     * @param value
     *            小文字hex 64桁
     */
    public record ListeningChecksumResponse(String algorithm, String value) {
        private static ListeningChecksumResponse of(ListeningPackageView.Checksum checksum) {
            return new ListeningChecksumResponse(checksum.algorithm(), checksum.value());
        }
    }

    /**
     * artwork が無い作品では {@code artworkAssetId} の項目自体を出さない（schema は項目の省略だけを許し、null
     * を拒む）。
     *
     * @param albumId
     *            作品のドメインID
     * @param title
     *            作品名
     * @param artworkAssetId
     *            カバー画像の assetId。確定済みで実測値を持つ画像が無ければ省略
     * @param tracks
     *            収録曲（曲順）。音源を要求しない
     */
    public record ListeningAlbumResponse(
            String albumId,
            String title,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String artworkAssetId,
            List<ListeningTrackResponse> tracks) {
        private static ListeningAlbumResponse of(ListeningPackageView.Album album) {
            return new ListeningAlbumResponse(
                    album.albumId(),
                    album.title(),
                    album.artworkAssetId().orElse(null),
                    toList(ListeningTrackResponse::of)
                            .apply(album.tracks()));
        }
    }

    /**
     * @param trackId
     *            収録曲のドメインID
     * @param trackNo
     *            曲番号
     * @param title
     *            曲名（入力が無ければチューン名から組んだ名）
     */
    public record ListeningTrackResponse(
            String trackId,
            int trackNo,
            String title) {
        private static ListeningTrackResponse of(ListeningPackageView.Track track) {
            return new ListeningTrackResponse(
                    track.trackId(),
                    track.trackNo(),
                    track.title());
        }
    }

    /**
     * @param playbackItemId
     *            再生項目のID。作品に紐づき、音源の差し替えでは変わらない
     * @param kind
     *            {@code album-crossfade}
     * @param albumId
     *            作品のドメインID
     * @param title
     *            表示名
     * @param audioAssetId
     *            音源の assetId
     * @param durationSeconds
     *            長さ（秒）
     */
    public record ListeningPlaybackItemResponse(
            String playbackItemId,
            String kind,
            String albumId,
            String title,
            String audioAssetId,
            double durationSeconds) {
        private static ListeningPlaybackItemResponse of(ListeningPackageView.Crossfade item) {
            return new ListeningPlaybackItemResponse(
                    item.playbackItemId(),
                    item.kind(),
                    item.albumId(),
                    item.title(),
                    item.audioAssetId().toString(),
                    item.durationSeconds());
        }
    }
}
