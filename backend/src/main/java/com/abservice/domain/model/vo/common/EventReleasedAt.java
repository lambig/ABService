package com.abservice.domain.model.vo.common;

import static io.github.lambig.funcifextension.predicate.By.having;
import static io.github.lambig.funcifextension.predicate.Predicates.and;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.domain.model.vo.event.EventName;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.Nullable;

/**
 * イベント頒布情報 Value Object
 *
 * <p>
 * コミケ、M3、ライブなどのイベントでアルバムが最初にリリース（頒布）された情報を表すValue
 * Objectです。イベント名、開催日、スペース番号、会場、補足情報を含みます。
 * </p>
 *
 * <p>
 * 「初出」は最初に頒布した1点の事実を指すため、開催日・スペース番号は単一です（複数日出展時の 日ごとの配置管理は
 * {@code ConfirmedEvent}（{@code EventToParticipate}）が別途担います）。
 * </p>
 *
 * <p>
 * 生成は2系統です。信頼できる内部生成には {@code of(...)}（不正時は例外）を、外部入力からの生成には
 * {@link #fromInput(String, BusinessDate, String, String, String)}（不正時は
 * {@code Failure} を返す）を使用します。
 * </p>
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
public final class EventReleasedAt implements ValueObject<EventReleasedAt> {
    /** イベント名 */
    private final EventName name;
    /** 開催日 */
    @Nullable
    private final BusinessDate date;
    /** スペース番号（例: 東A-01） */
    @Nullable
    private final String spaceNumber;
    /** 会場 */
    @Nullable
    private final String place;
    /** 補足情報 */
    @Nullable
    private final String note;

    @Override
    public boolean equivalentTo(EventReleasedAt other) {
        return Optional.ofNullable(other)
                .filter(
                        and(
                                having(EventReleasedAt::name).that(this.name::equivalentTo),
                                having(EventReleasedAt::date).thatEqualsTo(this.date),
                                having(EventReleasedAt::spaceNumber).thatEqualsTo(this.spaceNumber),
                                having(EventReleasedAt::place).thatEqualsTo(this.place),
                                having(EventReleasedAt::note).thatEqualsTo(this.note)))
                .isPresent();
    }

    /**
     * コンストラクタ
     *
     * @param name
     *            イベント名（必須）
     * @param date
     *            開催日（nullable）
     * @param spaceNumber
     *            スペース番号（nullable）
     * @param place
     *            会場（nullable）
     * @param note
     *            補足情報（nullable）
     */
    private EventReleasedAt(
            EventName name,
            @Nullable BusinessDate date,
            @Nullable String spaceNumber,
            @Nullable String place,
            @Nullable String note) {
        Policy.<EventName>of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "name",
                        "Event name cannot be null",
                        "NAME_REQUIRED"))
                .verify(name, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
        this.name = name;
        this.date = date;
        this.spaceNumber = spaceNumber;
        this.place = place;
        this.note = note;
    }

    /**
     * イベント名のみで生成
     *
     * @param name
     *            イベント名
     * @return EventReleasedAt
     */
    public static EventReleasedAt of(String name) {
        return new EventReleasedAt(
                new EventName(name),
                null,
                null,
                null,
                null);
    }

    /**
     * イベント名と開催日で生成
     *
     * @param name
     *            イベント名
     * @param date
     *            開催日
     * @return EventReleasedAt
     */
    public static EventReleasedAt of(String name, @Nullable BusinessDate date) {
        return new EventReleasedAt(
                new EventName(name),
                date,
                null,
                null,
                null);
    }

    /**
     * イベント名、開催日、スペース番号で生成
     *
     * @param name
     *            イベント名
     * @param date
     *            開催日
     * @param spaceNumber
     *            スペース番号
     * @return EventReleasedAt
     */
    public static EventReleasedAt of(
            String name,
            @Nullable BusinessDate date,
            @Nullable String spaceNumber) {
        return new EventReleasedAt(
                new EventName(name),
                date,
                spaceNumber,
                null,
                null);
    }

    /**
     * イベント名と年月日で生成
     *
     * @param name
     *            イベント名
     * @param year
     *            年
     * @param month
     *            月
     * @param dayOfMonth
     *            日
     * @return EventReleasedAt
     */
    public static EventReleasedAt atEvent(
            String name,
            int year,
            int month,
            int dayOfMonth) {
        return of(
                name,
                BusinessDate.of(
                        year,
                        month,
                        dayOfMonth));
    }

    /**
     * 名前・開催日・会場・スペース番号・補足情報の全項目で生成
     *
     * @param name
     *            イベント名
     * @param date
     *            開催日（nullable）
     * @param place
     *            会場（nullable）
     * @param spaceNumber
     *            スペース番号（nullable）
     * @param note
     *            補足情報（nullable）
     * @return EventReleasedAt
     */
    public static EventReleasedAt of(
            String name,
            @Nullable BusinessDate date,
            @Nullable String place,
            @Nullable String spaceNumber,
            @Nullable String note) {
        return new EventReleasedAt(
                new EventName(name),
                date,
                spaceNumber,
                place,
                note);
    }

    /**
     * 外部入力からイベント頒布情報を生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。イベント名が未指定・最大長超過の場合は {@code Failure}
     * として返します。{@link BusinessDate} は文字列パースを提供しないため、開催日は
     * 呼び出し側で解釈済みの値を渡してください。信頼できる内部生成には {@code of(...)} を使用してください。
     * </p>
     *
     * @param name
     *            イベント名を表す文字列
     * @param date
     *            開催日（呼び出し側で解釈済み。nullable）
     * @param place
     *            会場（nullable）
     * @param spaceNumber
     *            スペース番号（nullable）
     * @param note
     *            補足情報（nullable）
     * @return 成功時は {@code EventReleasedAt}、失敗時はエラー
     */
    public static Result<EventReleasedAt> fromInput(
            @Nullable String name,
            @Nullable BusinessDate date,
            @Nullable String place,
            @Nullable String spaceNumber,
            @Nullable String note) {
        return EventName.fromInput(name)
                .map(
                        n -> new EventReleasedAt(
                                n,
                                date,
                                spaceNumber,
                                place,
                                note));
    }
}
