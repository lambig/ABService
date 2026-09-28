package com.abservice.application.query.audio;

import com.abservice.domain.model.vo.album.TrackName;
import com.abservice.infrastructure.persistence.datasource.ListeningPackageRow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * 配布パッケージ（Manifest v2 の内容そのもの）。snapshot の平坦な行から組み、内容の digest を packageVersion
 * にする。
 *
 * <p>
 * 端末へ渡す項目をすべてここで確定させ、packageVersion はそれら全項目（packageVersion 自身を除く）の canonical
 * な表現の SHA-256 にする。schema の版・互換範囲・表示素材・音源の種別や必須フラグが変わっても版が変わり、
 * 端末は版の一致だけで更新の要否を判断できる。取得URL・保存キー・秘密は持たない。音源の assetId は登録IDで、
 * 再生項目は作品ごとに1件のクロスフェード。項目IDは作品に紐づけて固定し、音源の差し替えでは変えない。
 * </p>
 *
 * @param schemaVersion
 *            Manifest の schema 版
 * @param packageVersion
 *            内容の SHA-256（小文字hex）
 * @param compatibleAppVersion
 *            実行できるアプリの版の範囲
 * @param presentationAssetIds
 *            必須の表示素材の assetId（artwork は digest を持ってから足す）
 * @param assets
 *            音源（作品順。同じ実体は1件）
 * @param albums
 *            作品（公開向け一覧と同じ順）
 * @param playbackItems
 *            再生項目（作品順）
 */
public record ListeningPackageView(
        int schemaVersion,
        String packageVersion,
        AppVersionRange compatibleAppVersion,
        List<String> presentationAssetIds,
        List<AudioAsset> assets,
        List<Album> albums,
        List<Crossfade> playbackItems) {

    /** 現在配布する Manifest の schema 版と互換範囲。 */
    static final Contract CURRENT_CONTRACT = new Contract(
            2,
            new AppVersionRange(
                    List.of(
                            1,
                            10,
                            0),
                    List.of(
                            2,
                            0,
                            0)));
    static final String AUDIO_MEDIA_TYPE = "audio/flac";
    static final String CHECKSUM_ALGORITHM = "sha256";
    static final String CROSSFADE_KIND = "album-crossfade";
    /** artwork は確定画像の digest を持ってから足す。それまで表示素材は無い。 */
    private static final List<String> NO_PRESENTATION_ASSETS = List.of();
    private static final String PLAYBACK_ITEM_PREFIX = CROSSFADE_KIND + ":";
    private static final String FIELD_SEPARATOR = "\u001f";
    private static final String VERSION_SEPARATOR = ".";

    public static ListeningPackageView of(List<ListeningPackageRow> rows, String tuneTitleSeparator) {
        return of(
                CURRENT_CONTRACT,
                rows,
                tuneTitleSeparator);
    }

    static ListeningPackageView of(
            Contract contract,
            List<ListeningPackageRow> rows,
            String tuneTitleSeparator) {
        return of(
                contract,
                assetsOf(rows),
                albumsOf(rows, tuneTitleSeparator),
                itemsOf(rows));
    }

    private static ListeningPackageView of(
            Contract contract,
            List<AudioAsset> assets,
            List<Album> albums,
            List<Crossfade> items) {
        return new ListeningPackageView(
                contract.schemaVersion(),
                fingerprint(
                        contract,
                        NO_PRESENTATION_ASSETS,
                        assets,
                        albums,
                        items),
                contract.compatibleAppVersion(),
                NO_PRESENTATION_ASSETS,
                assets,
                albums,
                items);
    }

    private static List<Album> albumsOf(List<ListeningPackageRow> rows, String separator) {
        return groupedByAlbum(rows).values().stream()
                .map(albumRows -> album(albumRows, separator))
                .toList();
    }

    /** 行の並びは作品順を保つため、挿入順を保持する Map に集める。 */
    private static Map<String, List<ListeningPackageRow>> groupedByAlbum(List<ListeningPackageRow> rows) {
        return rows.stream().collect(
                Collectors.groupingBy(
                        ListeningPackageRow::albumId,
                        LinkedHashMap::new,
                        Collectors.toUnmodifiableList()));
    }

    private static Album album(List<ListeningPackageRow> rows, String separator) {
        return new Album(
                rows.getFirst().albumId(),
                rows.getFirst().albumTitle(),
                tracksOf(rows, separator));
    }

    private static List<Track> tracksOf(List<ListeningPackageRow> rows, String separator) {
        return rows.stream()
                .filter(row -> row.trackId() != null)
                .collect(
                        Collectors.groupingBy(
                                row -> Objects.requireNonNull(row.trackId()),
                                LinkedHashMap::new,
                                Collectors.toUnmodifiableList()))
                .values().stream()
                .map(trackRows -> track(trackRows, separator))
                .sorted(Comparator.comparingInt(Track::trackNo))
                .toList();
    }

    private static Track track(List<ListeningPackageRow> rows, String separator) {
        return new Track(
                Objects.requireNonNull(rows.getFirst().trackId()),
                Objects.requireNonNull(rows.getFirst().trackNo()),
                TrackName.of(
                        rows.getFirst().trackTitle(),
                        tuneTitlesOf(rows),
                        separator).value());
    }

    private static List<@Nullable String> tuneTitlesOf(List<ListeningPackageRow> rows) {
        return rows.stream()
                .filter(row -> row.tuneSeq() != null)
                .sorted(Comparator.comparingInt(row -> Objects.requireNonNull(row.tuneSeq())))
                .<@Nullable String>map(ListeningPackageRow::tuneTitle)
                .toList();
    }

    private static List<AudioAsset> assetsOf(List<ListeningPackageRow> rows) {
        return rows.stream()
                .map(
                        row -> new AudioAsset(
                                row.audioId(),
                                AUDIO_MEDIA_TYPE,
                                row.byteLength(),
                                new Checksum(CHECKSUM_ALGORITHM, row.sha256()),
                                true))
                .distinct()
                .toList();
    }

    private static List<Crossfade> itemsOf(List<ListeningPackageRow> rows) {
        return groupedByAlbum(rows).values().stream()
                .map(List::getFirst)
                .map(
                        row -> new Crossfade(
                                PLAYBACK_ITEM_PREFIX + row.albumId(),
                                CROSSFADE_KIND,
                                row.albumId(),
                                row.albumTitle(),
                                row.audioId(),
                                (double) row.totalSamples() / row.sampleRate()))
                .toList();
    }

    /**
     * Manifest の内容から版を決める。packageVersion 自身を除く全項目の並びと値だけに依存し、時刻や乱数を含めない。
     *
     * <p>
     * 各項目を1行の文字列に写し、改行で繋いだ列の SHA-256 を取る。区切りに現れない文字を境に使うため、 隣り合う値が入れ替わっても同じ列にはならない。
     * </p>
     */
    private static String fingerprint(
            Contract contract,
            List<String> presentationAssetIds,
            List<AudioAsset> assets,
            List<Album> albums,
            List<Crossfade> items) {
        return HexFormat.of().formatHex(
                sha256().digest(
                        Stream.of(
                                linesOf(contract),
                                Stream.of(lineOf(presentationAssetIds)),
                                assets.stream().map(ListeningPackageView::lineOf),
                                albums.stream().flatMap(ListeningPackageView::linesOf),
                                items.stream().map(ListeningPackageView::lineOf))
                                .flatMap(stream -> stream)
                                .collect(Collectors.joining("\n"))
                                .getBytes(StandardCharsets.UTF_8)));
    }

    private static Stream<String> linesOf(Contract contract) {
        return Stream.of(
                String.join(
                        FIELD_SEPARATOR,
                        "schema",
                        Integer.toString(contract.schemaVersion())),
                String.join(
                        FIELD_SEPARATOR,
                        "compatible",
                        versionOf(contract.compatibleAppVersion().minInclusive()),
                        versionOf(contract.compatibleAppVersion().maxExclusive())));
    }

    private static String lineOf(List<String> presentationAssetIds) {
        return Stream.concat(Stream.of("presentation"), presentationAssetIds.stream())
                .collect(Collectors.joining(FIELD_SEPARATOR));
    }

    private static String versionOf(List<Integer> version) {
        return version.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(VERSION_SEPARATOR));
    }

    private static Stream<String> linesOf(Album album) {
        return Stream.concat(
                Stream.of(
                        String.join(
                                FIELD_SEPARATOR,
                                "album",
                                album.albumId(),
                                album.title())),
                album.tracks().stream().map(ListeningPackageView::lineOf));
    }

    private static String lineOf(Track track) {
        return String.join(
                FIELD_SEPARATOR,
                "track",
                track.trackId(),
                Integer.toString(track.trackNo()),
                track.title());
    }

    private static String lineOf(AudioAsset asset) {
        return String.join(
                FIELD_SEPARATOR,
                "asset",
                asset.assetId().toString(),
                asset.mediaType(),
                Long.toString(asset.byteLength()),
                asset.checksum().algorithm(),
                asset.checksum().value(),
                Boolean.toString(asset.required()));
    }

    private static String lineOf(Crossfade item) {
        return String.join(
                FIELD_SEPARATOR,
                "item",
                item.playbackItemId(),
                item.kind(),
                item.albumId(),
                item.title(),
                item.audioAssetId().toString(),
                Double.toString(item.durationSeconds()));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }

    /** 内容に依らず決まる項目。schema の版と互換範囲は配布契約の一部で、変われば版も変わる。 */
    record Contract(int schemaVersion, AppVersionRange compatibleAppVersion) {
    }

    /**
     * @param minInclusive
     *            この版以上
     * @param maxExclusive
     *            この版未満
     */
    public record AppVersionRange(List<Integer> minInclusive, List<Integer> maxExclusive) {
    }

    /** 収録曲の説明情報。音源の有無を要求しない。 */
    public record Album(
            String albumId,
            String title,
            List<Track> tracks) {
    }

    public record Track(
            String trackId,
            int trackNo,
            String title) {
    }

    /** 確定済み音源の実体情報。URL・保存キーを持たない。 */
    public record AudioAsset(
            UUID assetId,
            String mediaType,
            long byteLength,
            Checksum checksum,
            boolean required) {
    }

    public record Checksum(String algorithm, String value) {
    }

    /** 作品単位のクロスフェード再生項目。 */
    public record Crossfade(
            String playbackItemId,
            String kind,
            String albumId,
            String title,
            UUID audioAssetId,
            double durationSeconds) {
    }

    /** 項目IDから作品を逆引きする。作品の項目でなければ空。 */
    public static Optional<String> albumIdOf(String playbackItemId) {
        return Optional.of(playbackItemId)
                .filter(id -> id.startsWith(PLAYBACK_ITEM_PREFIX))
                .map(id -> id.substring(PLAYBACK_ITEM_PREFIX.length()));
    }
}
