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
 * 配布パッケージ（Manifest v2 の内容）。snapshot の平坦な行から組み、内容の digest を packageVersion にする。
 *
 * <p>
 * 同じ内容なら同じ版、内容が変われば別の版になる。取得URL・保存キー・秘密は持たない。音源の assetId は登録IDで、
 * 再生項目は作品ごとに1件のクロスフェード。項目IDは作品に紐づけて固定し、音源の差し替えでは変えない。
 * </p>
 *
 * @param packageVersion
 *            内容の SHA-256（小文字hex）
 * @param albums
 *            作品（公開向け一覧の既定順）
 * @param assets
 *            音源（作品順。同じ実体は1件）
 * @param playbackItems
 *            再生項目（作品順）
 */
public record ListeningPackageView(
        String packageVersion,
        List<Album> albums,
        List<AudioAsset> assets,
        List<Crossfade> playbackItems) {

    private static final String PLAYBACK_ITEM_PREFIX = "album-crossfade:";
    private static final String FIELD_SEPARATOR = "\u001f";

    public static ListeningPackageView of(List<ListeningPackageRow> rows, String tuneTitleSeparator) {
        return of(
                albumsOf(rows, tuneTitleSeparator),
                assetsOf(rows),
                itemsOf(rows));
    }

    private static ListeningPackageView of(
            List<Album> albums,
            List<AudioAsset> assets,
            List<Crossfade> items) {
        return new ListeningPackageView(
                fingerprint(
                        albums,
                        assets,
                        items),
                albums,
                assets,
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
                                row.byteLength(),
                                row.sha256(),
                                durationOf(row)))
                .distinct()
                .toList();
    }

    private static List<Crossfade> itemsOf(List<ListeningPackageRow> rows) {
        return groupedByAlbum(rows).values().stream()
                .map(List::getFirst)
                .map(
                        row -> new Crossfade(
                                PLAYBACK_ITEM_PREFIX + row.albumId(),
                                row.albumId(),
                                row.albumTitle(),
                                row.audioId(),
                                durationOf(row)))
                .toList();
    }

    private static double durationOf(ListeningPackageRow row) {
        return (double) row.totalSamples() / row.sampleRate();
    }

    /**
     * 内容から版を決める。並びと値だけに依存し、時刻や乱数を含めない。
     *
     * <p>
     * 各要素を1行の文字列に写し、改行で繋いだ列の SHA-256 を取る。区切りに現れない文字を境に使うため、 隣り合う値が入れ替わっても同じ列にはならない。
     * </p>
     */
    private static String fingerprint(
            List<Album> albums,
            List<AudioAsset> assets,
            List<Crossfade> items) {
        return HexFormat.of().formatHex(
                sha256().digest(
                        Stream.of(
                                albums.stream().flatMap(ListeningPackageView::linesOf),
                                assets.stream().map(ListeningPackageView::lineOf),
                                items.stream().map(ListeningPackageView::lineOf))
                                .flatMap(stream -> stream)
                                .collect(Collectors.joining("\n"))
                                .getBytes(StandardCharsets.UTF_8)));
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
                Long.toString(asset.byteLength()),
                asset.sha256(),
                Double.toString(asset.durationSeconds()));
    }

    private static String lineOf(Crossfade item) {
        return String.join(
                FIELD_SEPARATOR,
                "item",
                item.playbackItemId(),
                item.albumId(),
                item.title(),
                item.audioAssetId().toString());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
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
            long byteLength,
            String sha256,
            double durationSeconds) {
    }

    /** 作品単位のクロスフェード再生項目。 */
    public record Crossfade(
            String playbackItemId,
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
