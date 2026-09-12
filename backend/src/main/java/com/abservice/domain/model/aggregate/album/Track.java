package com.abservice.domain.model.aggregate.album;

import static com.abservice.lib.Optionals.optionally;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.model.AggregateFactory;
import com.abservice.domain.model.DomainConstructor;
import com.abservice.domain.model.DomainFactory;
import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.entity.DomainEntity;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.album.TrackName;
import com.abservice.domain.model.vo.album.TrackTitle;
import com.abservice.domain.model.vo.album.TrackTuneTitle;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.Credit;
import com.abservice.domain.model.vo.common.Url;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NullUnmarked;

/**
 * トラック（集約内エンティティ）
 *
 * <p>
 * アルバムを構成する1トラックを表します。同じチューン構成であっても、改訂（revise）したものは別Trackとして扱います。
 * </p>
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class Track implements DomainEntity<Track, Track.Id> {
    /** トラックID */
    @EqualsAndHashCode.Include
    @NonNull
    private final Id id;
    /** トラック番号 */
    @NonNull
    private final Integer trackNo;
    /**
     * 入力されたトラックタイトル。
     *
     * <p>
     * 省略できます（#360）。省略したトラックの名は、そのトラックが持つチューン名を繋いだものになります。名を問う ときは、この項目を直に読まず
     * {@link #name(String)} を通してください。
     * </p>
     */
    @Nullable
    private final TrackTitle title;
    /** nullの場合はAlbumのartistCreditを継承 */
    @Nullable
    private final ArtistCredit artistCredit;
    /** トラック内のチューンのリスト */
    @NonNull
    private final List<TrackTune> tunes;

    /** trackNo必須違反時のエラー */
    private static final ErrorResult TRACK_NO_REQUIRED_ERROR = new ErrorResult(
            "trackNo",
            "Track number cannot be null",
            "TRACK_NO_REQUIRED");

    /** 名を答えられない（タイトルも、名を持つチューンも無い）場合のエラー */
    private static final ErrorResult NAME_UNRESOLVABLE_ERROR = new ErrorResult(
            "title",
            "Track title is required unless the track has at least one named tune",
            "TRACK_NAME_UNRESOLVABLE");

    @DomainConstructor
    private Track(@NonNull Id id, @NonNull Integer trackNo, @Nullable TrackTitle title,
            @Nullable ArtistCredit artistCredit, @NonNull List<TrackTune> tunes) {
        this.id = id;
        this.trackNo = trackNo;
        this.title = title;
        this.artistCredit = artistCredit;
        this.tunes = tunes;
    }

    @DomainFactory
    private static @NonNull Track factory(@Nullable Id id, @Nullable Integer trackNo, @Nullable TrackTitle title,
            @Nullable ArtistCredit artistCredit, @Nullable List<TrackTune> tunes) {
        return Policy.<Stub>all(
                Policy.of(
                        self -> self.trackNo() != null,
                        TRACK_NO_REQUIRED_ERROR),
                Policy.of(
                        Stub::hasResolvableName,
                        NAME_UNRESOLVABLE_ERROR))
                .verify(
                        new Stub(
                                id,
                                trackNo,
                                title,
                                artistCredit,
                                tunes),
                        Stub::asTrack)
                .resolve(Policy::illegalArgument);
    }

    @NullUnmarked
    private record Stub(Id id, Integer trackNo, TrackTitle title, ArtistCredit artistCredit, List<TrackTune> tunes) {

        @AggregateFactory
        @NonNull
        Track asTrack() {
            return new Track(
                    Objects.requireNonNull(id),
                    Objects.requireNonNull(trackNo),
                    title(),
                    artistCredit(),
                    Objects.requireNonNull(tunes));
        }

        /**
         * タイトルを持たないトラックは、名を持つチューンを少なくとも1つ持つ（#360）
         *
         * @return 名を答えられる場合は true
         */
        boolean hasResolvableName() {
            return TrackName.isResolvable(titleValue(), tuneTitleValues(tunes()));
        }

        private @Nullable String titleValue() {
            return Optional.ofNullable(title())
                    .map(TrackTitle::value)
                    .orElse(null);
        }
    }

    /**
     * このトラックの名を答える
     *
     * <p>
     * タイトルを持つトラックはそれを、持たないトラックはチューン名を繋いだものを名にします（#360）。呼び出し側が
     * 「名があるか」で分岐しなくて済むよう、名を問う口はここだけにします。
     * </p>
     *
     * @param tuneTitleSeparator
     *            チューン名を繋ぐ区切り
     * @return トラックの名
     */
    public @NonNull TrackName name(@NonNull String tuneTitleSeparator) {
        return TrackName.of(
                Optional.ofNullable(title)
                        .map(TrackTitle::value)
                        .orElse(null),
                tuneTitleValues(tunes),
                tuneTitleSeparator);
    }

    /**
     * チューン名の並び（登場順）。名を持たないチューン（MC・環境音など）は null のまま残す
     *
     * @param tunes
     *            チューン構成（nullable。未指定は構成なしとして扱う）
     * @return チューン名の並び（登場順）
     */
    private static List<@Nullable String> tuneTitleValues(@Nullable List<TrackTune> tunes) {
        return Optional.ofNullable(tunes)
                .stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparing(TrackTune::seq))
                .<@Nullable String>map(Track::tuneTitleValue)
                .toList();
    }

    /**
     * チューン1件の名。名を持たないチューン（MC・環境音など）は null
     *
     * @param tune
     *            チューン構成1件
     * @return チューン名（名を持たない場合は null）
     */
    private static @Nullable String tuneTitleValue(TrackTune tune) {
        return Optional.ofNullable(tune.tuneTitle())
                .map(TrackTuneTitle::value)
                .orElse(null);
    }

    /**
     * チューン構成を持たない新規トラックを生成
     *
     * <p>
     * 構成を持たないトラックは名の元をタイトルにしか持てないため、タイトルは必須になります（#360）。構成ごと 組み立てる場合は
     * {@link #create(Integer, TrackTitle, ArtistCredit, List)} を使ってください。
     * </p>
     *
     * @param trackNo
     *            トラック番号
     * @param title
     *            トラックタイトル
     * @param artistCredit
     *            アーティストクレジット（nullable）
     * @return 新規Track
     */
    @DomainFactory
    public static @NonNull Track create(@NonNull Integer trackNo, @NonNull TrackTitle title,
            @Nullable ArtistCredit artistCredit) {
        return Track.create(
                trackNo,
                title,
                artistCredit,
                Collections.emptyList());
    }

    /**
     * 新規トラックを生成
     *
     * @param trackNo
     *            トラック番号
     * @param title
     *            トラックタイトル（nullable。省略したときはチューン名が名になる）
     * @param artistCredit
     *            アーティストクレジット（nullable）
     * @param tunes
     *            チューン構成
     * @return 新規Track
     */
    @DomainFactory
    public static @NonNull Track create(@NonNull Integer trackNo, @Nullable TrackTitle title,
            @Nullable ArtistCredit artistCredit, @NonNull List<TrackTune> tunes) {
        return Track.factory(
                Id.generate(),
                trackNo,
                title,
                artistCredit,
                tunes);
    }

    /**
     * 外部入力からトラックを生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。{@code trackNo} の必須検証と、タイトルを持つ場合の
     * その検証を担います。信頼できる内部生成には {@link #create} を使用してください。
     * </p>
     *
     * <p>
     * タイトルは省略できます（#360）。省略したトラックの名はチューン名を繋いだものになるため、<b>名を持つチューンを
     * 少なくとも1つ持つ必要があります</b>。満たさない入力は名を答えられないトラックになるため、ここで落とします。
     * </p>
     *
     * @param trackNo
     *            トラック番号
     * @param title
     *            トラックタイトルを表す文字列（nullable）
     * @param artistCredit
     *            アーティストクレジット（nullable）
     * @param tunes
     *            チューン構成
     * @return 成功時は {@code Track}、失敗時はエラー
     */
    public static Result<Track> fromInput(@Nullable Integer trackNo, @Nullable String title,
            @Nullable ArtistCredit artistCredit, @NonNull List<TrackTune> tunes) {
        return Track.fromInput(
                Id.generate(),
                trackNo,
                title,
                artistCredit,
                tunes);
    }

    /**
     * 外部入力から、既にあるトラックを組み直します。
     *
     * <p>
     * 更新のユースケース（PUT風の全項目置換）が使います。検証の規則は
     * {@link #fromInput(Integer, String, ArtistCredit, List)}
     * と同じで、IDだけを引き継ぎます——**規則を呼ぶ側へ写さない**ため、入口を分けずに IDの出どころだけを変えます。
     * </p>
     *
     * @param id
     *            引き継ぐトラックID
     * @param trackNo
     *            トラック番号
     * @param title
     *            トラックタイトルを表す文字列（nullable）
     * @param artistCredit
     *            アーティストクレジット（nullable）
     * @param tunes
     *            チューン構成
     * @return 成功時は {@code Track}、失敗時はエラー
     */
    public static Result<Track> fromInput(@NonNull Id id, @Nullable Integer trackNo, @Nullable String title,
            @Nullable ArtistCredit artistCredit, @NonNull List<TrackTune> tunes) {
        return Result.zip(
                Policy.<Integer>of(
                        Objects::nonNull,
                        () -> new ErrorResult(
                                "trackNo",
                                "Track number is required",
                                "TRACK_NO_REQUIRED"))
                        .verify(trackNo, Function.identity()),
                resolveTitle(title, tunes),
                (validTrackNo, validTitle) -> Track.factory(
                        id,
                        validTrackNo,
                        validTitle.orElse(null),
                        artistCredit,
                        tunes));
    }

    /**
     * 省略できるタイトルを解く。
     *
     * <p>
     * 空白のみの入力は「省略」と同じに扱います。書式として区別できない（画面の入力欄は空文字を送る）ため、区別すると
     * 送り手ごとに結果が変わります。省略したうえで名を持つチューンも無いときだけ、タイトルの位置へエラーを返します。
     * </p>
     *
     * @param title
     *            トラックタイトルを表す文字列（nullable）
     * @param tunes
     *            チューン構成
     * @return 成功時はタイトル（省略時は空）、失敗時はエラー
     */
    private static Result<Optional<TrackTitle>> resolveTitle(@Nullable String title, @NonNull List<TrackTune> tunes) {
        return Optional.ofNullable(title)
                .filter(StringUtils::isNotBlank)
                .map(Track::presentTitle)
                .orElseGet(() -> absentTitle(tunes));
    }

    /**
     * 入力されたタイトルを検証する
     *
     * @param title
     *            トラックタイトルを表す文字列
     * @return 成功時はタイトル、失敗時はエラー
     */
    private static Result<Optional<TrackTitle>> presentTitle(String title) {
        return TrackTitle.fromInput(title)
                .withErrorField("title")
                .map(Optional::of);
    }

    /**
     * タイトルを省略した入力を受け入れるかどうかを、チューンの側から決める
     *
     * @param tunes
     *            チューン構成
     * @return 名を持つチューンがあれば空のタイトル、無ければエラー
     */
    private static Result<Optional<TrackTitle>> absentTitle(@NonNull List<TrackTune> tunes) {
        return Policy.<List<TrackTune>>of(
                candidates -> TrackName.isResolvable(null, tuneTitleValues(candidates)),
                NAME_UNRESOLVABLE_ERROR)
                .verify(tunes, ignored -> Optional.<TrackTitle>empty());
    }

    /**
     * 永続化層からの再構成
     *
     * @param id
     *            トラックID
     * @param trackNo
     *            トラック番号
     * @param title
     *            トラックタイトル（nullable）
     * @param artistCredit
     *            アーティストクレジット（nullable）
     * @param tunes
     *            チューンリスト
     * @return 再構成されたTrack
     */
    @DomainFactory
    public static @NonNull Track reconstruct(@NonNull Id id, @NonNull Integer trackNo, @Nullable TrackTitle title,
            @Nullable ArtistCredit artistCredit, @NonNull List<TrackTune> tunes) {
        return Track.factory(
                id,
                trackNo,
                title,
                artistCredit,
                tunes);
    }

    /**
     * トラックタイトルを変更
     *
     * <p>
     * {@code null} を渡すとタイトルを落とします。落とした後の名はチューン名を繋いだものになるため、名を持つチューンを
     * 持たないトラックでは落とせません（#360）。
     * </p>
     *
     * @param newTitle
     *            新しいトラックタイトル（nullable）
     * @return 更新されたTrack
     */
    public @NonNull Track changeTitle(@Nullable TrackTitle newTitle) {
        return Track.factory(
                id,
                trackNo,
                newTitle,
                artistCredit,
                tunes);
    }

    /**
     * アーティストクレジットを変更
     *
     * @param newArtistCredit
     *            新しいアーティストクレジット
     * @return 更新されたTrack
     */
    public @NonNull Track changeArtistCredit(@Nullable ArtistCredit newArtistCredit) {
        return Track.factory(
                id,
                trackNo,
                title,
                newArtistCredit,
                tunes);
    }

    /**
     * チューンを追加
     *
     * @param tune
     *            追加するチューン
     * @return 更新されたTrack
     */
    public @NonNull Track addTune(@NonNull TrackTune tune) {
        final var validatedTune = Policy.<TrackTune>of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "tune",
                        "Tune cannot be null",
                        "TUNE_REQUIRED"))
                .verify(tune, Function.identity())
                .resolve(Policy::illegalArgument);
        Policy.<TrackTune>of(
                t -> tunes.stream().noneMatch(t::equivalentTo),
                () -> new ErrorResult(
                        "seq",
                        "Tune seq " + validatedTune.seq() + " already exists in this track",
                        "TUNE_SEQ_DUPLICATE"))
                .verify(validatedTune, Function.identity())
                .resolve(BusinessRuleViolationException::fromErrors);
        return Track.factory(
                id,
                trackNo,
                title,
                artistCredit,
                Stream.concat(tunes.stream(), Stream.of(validatedTune)).toList());
    }

    /**
     * チューンを削除
     *
     * @param seq
     *            削除するチューンのseq
     * @return 更新されたTrack
     */
    public @NonNull Track removeTune(@NonNull Integer seq) {
        final var validatedSeq = Policy.<Integer>of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "seq",
                        "Seq cannot be null",
                        "SEQ_REQUIRED"))
                .verify(seq, Function.identity())
                .resolve(Policy::illegalArgument);
        tunes.stream().filter(t -> t.hasId(validatedSeq)).findFirst()
                .orElseThrow(() -> new BusinessRuleViolationException("Tune with seq " + validatedSeq + " not found"));
        return Track.factory(
                id,
                trackNo,
                title,
                artistCredit,
                tunes.stream().filter(not(t -> t.hasId(validatedSeq))).toList());
    }

    /**
     * チューンの上書き情報（クレジット上書き・リンクURL）を更新
     *
     * <p>
     * {@code tuneId}はトラックが表す構成の事実の一部として生成後は不変のため対象外とする。誤認識していたチューンの 再識別・訂正は
     * {@link #removeTune(Integer)} と {@link #addTune(TrackTune)} の組み合わせで表現する。
     * </p>
     *
     * @param seq
     *            対象チューンのシーケンス番号
     * @param composerCreditOverride
     *            新しい作曲者クレジット上書き（nullable）
     * @param arrangerCreditOverride
     *            新しいアレンジャークレジット上書き（nullable）
     * @param linkUrl
     *            新しいリンクURL（nullable）
     * @return 更新されたTrack
     */
    public @NonNull Track updateTune(@NonNull Integer seq, @Nullable Credit composerCreditOverride,
            @Nullable Credit arrangerCreditOverride, @Nullable Url linkUrl) {
        final var validatedSeq = Policy.<Integer>of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "seq",
                        "Seq cannot be null",
                        "SEQ_REQUIRED"))
                .verify(seq, Function.identity())
                .resolve(Policy::illegalArgument);
        final var updatedTune = tunes.stream().filter(t -> t.hasId(validatedSeq)).findFirst()
                .orElseThrow(() -> new BusinessRuleViolationException("Tune with seq " + validatedSeq + " not found"))
                .changeComposerCreditOverride(composerCreditOverride)
                .changeArrangerCreditOverride(arrangerCreditOverride)
                .changeLinkUrl(linkUrl);
        return tunes.stream()
                .map(
                        t -> t.hasId(validatedSeq)
                                ? updatedTune
                                : t)
                .collect(optionally(toUnmodifiableList()))
                .map(
                        newTunes -> Track.factory(
                                id,
                                trackNo,
                                title,
                                artistCredit,
                                newTunes))
                .get();
    }

    /**
     * チューン構成を丸ごと置き換える
     *
     * <p>
     * トラックの更新（PUT風の全項目置換）が、入力の持つチューン構成をそのまま反映するための操作です。 {@code seq}
     * はトラック内で一意である必要があります。個別の追加・削除・更新は {@link #addTune(TrackTune)} /
     * {@link #removeTune(Integer)} / {@link #updateTune} で表現します。
     * </p>
     *
     * @param newTunes
     *            新しいチューン構成の一覧
     * @return 更新されたTrack
     */
    public @NonNull Track replaceTunes(@NonNull List<TrackTune> newTunes) {
        final var validatedTunes = Policy.<List<TrackTune>>of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "tunes",
                        "Tunes cannot be null",
                        "TUNES_REQUIRED"))
                .verify(newTunes, Function.identity())
                .resolve(Policy::illegalArgument);
        Policy.<List<TrackTune>>of(
                Track::hasUniqueSeqs,
                () -> new ErrorResult(
                        "seq",
                        "Tune seq must be unique in this track",
                        "TUNE_SEQ_DUPLICATE"))
                .verify(validatedTunes, Function.identity())
                .resolve(BusinessRuleViolationException::fromErrors);
        return Track.factory(
                id,
                trackNo,
                title,
                artistCredit,
                List.copyOf(validatedTunes));
    }

    private static boolean hasUniqueSeqs(List<TrackTune> tunes) {
        return tunes.stream().map(TrackTune::seq).distinct().count() == tunes.size();
    }

    /**
     * チューンリストを取得（不変）
     *
     * @return チューンリストの不変コピー
     */
    public @NonNull List<TrackTune> getTunes() {
        return Collections.unmodifiableList(tunes);
    }

    @Override
    public @NonNull Id id() {
        return id;
    }

    /**
     * Track ID型
     *
     * @param value
     *            ID値（UUIDv7形式の文字列）
     */
    public record Id(@NonNull String value) implements EntityId<Track> {
        public Id {
            idPolicy(value)
                    .verify(value, Function.identity())
                    .resolve(Policy::illegalArgument);
        }

        private static Policy<String> idPolicy(@Nullable String value) {
            return Policy.all(
                    Policy.of(
                            StringUtils::isNotBlank,
                            () -> new ErrorResult(
                                    "value",
                                    "Track ID cannot be blank",
                                    "ID_BLANK")),
                    Policy.of(
                            EntityId::isValidUuid,
                            () -> new ErrorResult("value", "Track ID must be a valid UUID: " + value,
                                    "ID_INVALID_UUID")));
        }

        /**
         * UUIDv7を生成してTrack.Idを作成
         *
         * @return 新規Id
         */
        public static @NonNull Id generate() {
            return new Id(EntityId.generateUuidV7());
        }

        /**
         * 文字列からTrack.Idを生成
         *
         * @param value
         *            ID値（UUIDv7形式の文字列）
         * @return Id
         */
        public static @NonNull Id of(@NonNull String value) {
            return new Id(value);
        }

        /**
         * 外部入力（文字列）からTrack.Idを生成します。
         *
         * <p>
         * 例外をスローせず、検証結果を {@link Result} で返します。 信頼できる内部生成には {@link #of(String)}
         * を使用してください。
         * </p>
         *
         * @param value
         *            ID値を表す文字列
         * @return 成功時は {@code Id}、失敗時はエラー
         */
        public static Result<Id> fromInput(@Nullable String value) {
            return idPolicy(value)
                    .verify(value, Id::new);
        }
    }
}
