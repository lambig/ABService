package com.abservice.application.query.audio;

import com.abservice.domain.model.vo.album.TrackName;
import com.abservice.infrastructure.persistence.datasource.ListeningPackageRow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * 配布パッケージ（Manifest v3 の内容そのもの）。snapshot の平坦な行から組み、内容の digest を packageVersion
 * にする。v3 は作品ごとに表示情報（名義・リリース日・カタログナンバー・説明文の原文と形式・原作の出典）を持つ。
 *
 * <p>
 * 端末へ渡す項目をすべてここで確定させ、packageVersion はそれら全項目（packageVersion 自身を除く）の canonical
 * な表現の SHA-256 にする。schema の版・互換範囲・表示素材・音源の種別や必須フラグが変わっても版が変わり、
 * 端末は版の一致だけで更新の要否を判断できる。取得URL・保存キー・秘密は持たない。音源の assetId は登録IDで、
 * 再生項目は作品ごとに1件のクロスフェード。項目IDは作品に紐づけて固定し、音源の差し替えでは変えない。 artwork
 * は確定済みで実測値を持つカバー画像だけを表示素材として載せ、assetId は配信キーにする。
 * </p>
 *
 * @param schemaVersion
 *            Manifest の schema 版
 * @param packageVersion
 *            内容の SHA-256（小文字hex）
 * @param compatibleAppVersion
 *            実行できるアプリの版の範囲
 * @param presentationAssetIds
 *            必須の表示素材の assetId（作品の artwork。作品順、同じ画像は1件）
 * @param assets
 *            音源と表示素材（作品順。同じ実体は1件）
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
        List<Asset> assets,
        List<Album> albums,
        List<Crossfade> playbackItems) {

    /** 現在配布する Manifest の schema 版と互換範囲。 */
    static final Contract CURRENT_CONTRACT = new Contract(
            3,
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
    private static final String PLAYBACK_ITEM_PREFIX = CROSSFADE_KIND + ":";
    private static final String LENGTH_TERMINATOR = ":";

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
            List<Asset> assets,
            List<Album> albums,
            List<Crossfade> items) {
        final var presentationAssetIds = presentationAssetIdsOf(albums);
        return new ListeningPackageView(
                contract.schemaVersion(),
                fingerprint(
                        contract,
                        presentationAssetIds,
                        assets,
                        albums,
                        items),
                contract.compatibleAppVersion(),
                presentationAssetIds,
                assets,
                albums,
                items);
    }

    /** 表示素材は作品の artwork。作品順に並べ、複数の作品が同じ画像を使っても1件にする。 */
    private static List<String> presentationAssetIdsOf(List<Album> albums) {
        return albums.stream()
                .map(Album::artworkAssetId)
                .flatMap(Optional::stream)
                .distinct()
                .toList();
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
                presentationOf(rows.getFirst()),
                Optional.ofNullable(rows.getFirst().artworkKey()),
                tracksOf(rows, separator));
    }

    /** 説明文は原文と形式の組でだけ持つ。原文が無ければ形式も載せない。 */
    private static Presentation presentationOf(ListeningPackageRow row) {
        return new Presentation(
                row.artistDisplayName(),
                Optional.ofNullable(row.releaseDate())
                        .map(LocalDate::toString),
                Optional.ofNullable(row.catalogNumber()),
                Optional.ofNullable(row.description())
                        .map(text -> new Description(text, row.descriptionFormat())),
                Optional.ofNullable(row.originalWorkNote()));
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

    /** 作品ごとに音源、次いで artwork の順で並べ、同じ実体は先に現れた位置に1件だけ残す。 */
    private static List<Asset> assetsOf(List<ListeningPackageRow> rows) {
        return groupedByAlbum(rows).values().stream()
                .map(List::getFirst)
                .flatMap(row -> Stream.concat(Stream.of(audioAsset(row)), artworkAsset(row).stream()))
                .distinct()
                .toList();
    }

    private static Asset audioAsset(ListeningPackageRow row) {
        return new Asset(
                row.audioId().toString(),
                AUDIO_MEDIA_TYPE,
                row.byteLength(),
                new Checksum(CHECKSUM_ALGORITHM, row.sha256()),
                true);
    }

    private static Optional<Asset> artworkAsset(ListeningPackageRow row) {
        return Optional.ofNullable(row.artworkKey())
                .map(
                        key -> new Asset(
                                key,
                                Objects.requireNonNull(row.artworkContentType()),
                                Objects.requireNonNull(row.artworkByteLength()),
                                new Checksum(CHECKSUM_ALGORITHM, Objects.requireNonNull(row.artworkSha256())),
                                true));
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
     * 各項目を「UTF-8 のバイト長、区切り、値」の連結で書き並べ、その列の SHA-256 を取る。長さが先に確定するため、値に改行や
     * 制御文字が含まれても境界がずれず、異なる内容が同じ列になることはない（prefix-free な符号）。要素数が変わる並びは
     * 要素数を先頭に置き、レコードは種別の札を先頭に置いて札ごとに項目数を固定する。生の区切り文字で繋ぐ方式は、値に
     * 区切り文字が含まれると別の内容が同じ列になりうる。
     * </p>
     */
    private static String fingerprint(
            Contract contract,
            List<String> presentationAssetIds,
            List<Asset> assets,
            List<Album> albums,
            List<Crossfade> items) {
        return HexFormat.of().formatHex(
                sha256().digest(
                        Stream.of(
                                fieldsOf(contract),
                                sequenceOf(presentationAssetIds, Stream::of),
                                sequenceOf(assets, ListeningPackageView::fieldsOf),
                                sequenceOf(albums, ListeningPackageView::fieldsOf),
                                sequenceOf(items, ListeningPackageView::fieldsOf))
                                .flatMap(stream -> stream)
                                .map(ListeningPackageView::lengthPrefixed)
                                .collect(Collectors.joining())
                                .getBytes(StandardCharsets.UTF_8)));
    }

    private static String lengthPrefixed(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length + LENGTH_TERMINATOR + value;
    }

    private static <T> Stream<String> sequenceOf(List<T> elements, Function<T, Stream<String>> fields) {
        return Stream.concat(
                Stream.of(Integer.toString(elements.size())),
                elements.stream().flatMap(fields));
    }

    private static Stream<String> fieldsOf(Contract contract) {
        return Stream.of(
                Stream.of(
                        "schema",
                        Integer.toString(contract.schemaVersion()),
                        "compatible"),
                sequenceOf(contract.compatibleAppVersion().minInclusive(), ListeningPackageView::fieldOf),
                sequenceOf(contract.compatibleAppVersion().maxExclusive(), ListeningPackageView::fieldOf))
                .flatMap(stream -> stream);
    }

    private static Stream<String> fieldOf(Integer number) {
        return Stream.of(Integer.toString(number));
    }

    /**
     * 任意項目（表示情報・artwork）の有無は要素数 0 か 1 の並びで表す。値の無い項目と空文字列の値を区別し、 隣の項目と混ざらないようにする。
     */
    private static Stream<String> fieldsOf(Album album) {
        return Stream.of(
                Stream.of(
                        "album",
                        album.albumId(),
                        album.title()),
                fieldsOf(album.presentation()),
                sequenceOf(album.artworkAssetId().stream().toList(), Stream::of),
                sequenceOf(album.tracks(), ListeningPackageView::fieldsOf))
                .flatMap(stream -> stream);
    }

    private static Stream<String> fieldsOf(Presentation presentation) {
        return Stream.of(
                Stream.of(presentation.artistDisplayName()),
                sequenceOf(presentation.releaseDate().stream().toList(), Stream::of),
                sequenceOf(presentation.catalogNumber().stream().toList(), Stream::of),
                sequenceOf(
                        presentation.description().stream().toList(),
                        description -> Stream.of(description.text(), description.format())),
                sequenceOf(presentation.originalWorkNote().stream().toList(), Stream::of))
                .flatMap(stream -> stream);
    }

    private static Stream<String> fieldsOf(Track track) {
        return Stream.of(
                "track",
                track.trackId(),
                Integer.toString(track.trackNo()),
                track.title());
    }

    private static Stream<String> fieldsOf(Asset asset) {
        return Stream.of(
                "asset",
                asset.assetId(),
                asset.mediaType(),
                Long.toString(asset.byteLength()),
                asset.checksum().algorithm(),
                asset.checksum().value(),
                Boolean.toString(asset.required()));
    }

    private static Stream<String> fieldsOf(Crossfade item) {
        return Stream.of(
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

    /**
     * 収録曲の説明情報。音源の有無を要求しない。
     *
     * @param artworkAssetId
     *            カバー画像の assetId（配信キー）。確定済みで実測値を持つ画像が無ければ空
     */
    public record Album(
            String albumId,
            String title,
            Presentation presentation,
            Optional<String> artworkAssetId,
            List<Track> tracks) {
    }

    /**
     * 作品の表示情報。canonical な Album の事実だけを持ち、layout・scene・effect は持たない。値の無い項目は空。
     *
     * @param artistDisplayName
     *            作品の名義
     * @param releaseDate
     *            リリース日（ISO-8601 の日付）
     * @param catalogNumber
     *            カタログナンバー
     * @param description
     *            説明文の原文と形式。HTML にはしない
     * @param originalWorkNote
     *            原作の出典の記述
     */
    public record Presentation(
            String artistDisplayName,
            Optional<String> releaseDate,
            Optional<String> catalogNumber,
            Optional<Description> description,
            Optional<String> originalWorkNote) {
    }

    /**
     * @param text
     *            説明文の原文
     * @param format
     *            {@code PLAIN_TEXT} / {@code MARKDOWN}
     */
    public record Description(String text, String format) {
    }

    public record Track(
            String trackId,
            int trackNo,
            String title) {
    }

    /** 取得対象の実体（音源と表示素材）。URL・保存キーを持たない。 */
    public record Asset(
            String assetId,
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
