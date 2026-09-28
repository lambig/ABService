package com.abservice.presentation.rest.audio.response;

import static com.abservice.lib.Iterables.toList;

import com.abservice.application.query.audio.ListeningPackageView;
import java.util.List;

/**
 * 端末へ渡す Manifest v2（{@code packages/installation} の schema v2 と同じ項目だけを持つ）。
 *
 * <p>
 * 項目を足すと端末側の厳密な検証が拒否するため、schema と一致する形に保つ。URL・保存キー・秘密は含まない。 artwork は
 * presentation asset として別途足す。
 * </p>
 *
 * @param schemaVersion
 *            常に 2
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

    private static final int SCHEMA_VERSION = 2;
    private static final List<Integer> MIN_APP_VERSION = List.of(
            1,
            10,
            0);
    private static final List<Integer> MAX_APP_VERSION = List.of(
            2,
            0,
            0);

    public static ListeningPackageResponse of(ListeningPackageView view) {
        return new ListeningPackageResponse(
                SCHEMA_VERSION,
                view.packageVersion(),
                new ListeningAppVersionRangeResponse(MIN_APP_VERSION, MAX_APP_VERSION),
                List.of(),
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
    }

    /**
     * @param assetId
     *            登録ID。取得URLではない
     * @param mediaType
     *            {@code audio/flac}
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
        private static ListeningAssetResponse of(ListeningPackageView.AudioAsset asset) {
            return new ListeningAssetResponse(
                    asset.assetId().toString(),
                    "audio/flac",
                    asset.byteLength(),
                    new ListeningChecksumResponse("sha256", asset.sha256()),
                    true);
        }
    }

    /**
     * @param algorithm
     *            {@code sha256}
     * @param value
     *            小文字hex 64桁
     */
    public record ListeningChecksumResponse(String algorithm, String value) {
    }

    /**
     * @param albumId
     *            作品のドメインID
     * @param title
     *            作品名
     * @param tracks
     *            収録曲（曲順）。音源を要求しない
     */
    public record ListeningAlbumResponse(
            String albumId,
            String title,
            List<ListeningTrackResponse> tracks) {
        private static ListeningAlbumResponse of(ListeningPackageView.Album album) {
            return new ListeningAlbumResponse(
                    album.albumId(),
                    album.title(),
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
                    "album-crossfade",
                    item.albumId(),
                    item.title(),
                    item.audioAssetId().toString(),
                    item.durationSeconds());
        }
    }
}
