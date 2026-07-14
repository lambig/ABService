package com.abservice.domain.model.vo.event;

import static com.abservice.domain.model.DomainObject.asType;
import static io.github.lambig.funcifextension.predicate.By.having;
import static io.github.lambig.funcifextension.predicate.Predicates.and;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.abservice.domain.model.policy.Policies;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.lib.ErrorResult;
import org.apache.commons.collections4.CollectionUtils;
import org.jspecify.annotations.Nullable;

/**
 * 不参加確定イベント Value Object
 *
 * <p>
 * イベントへの参加が不可能になった状態を表します。 全日落選、キャンセル、イベント中止などのケースで使用します。
 * </p>
 *
 * <p>
 * 重要：部分当選・部分落選の扱い
 * </p>
 * <ul>
 * <li>1日でも当選していれば、全体としてSelectedEventとして扱う</li>
 * <li>DeclinedEventは全日落選の場合のみ使用</li>
 * <li>部分落選の日程はSelectedEvent.declinedDatesに記録</li>
 * </ul>
 *
 * <p>
 * 使用例：
 * </p>
 * <ul>
 * <li>抽選で全日落選</li>
 * <li>ユーザーによる参加キャンセル</li>
 * <li>イベント主催者による中止</li>
 * </ul>
 *
 * @param name
 *            イベント名（必須）
 * @param declinedDates
 *            不参加確定した日程リスト（必須、1つ以上）
 * @param place
 *            会場（nullable）
 * @param reason
 *            不参加理由（必須）
 */
public record DeclinedEvent(EventName name, List<BusinessDate> declinedDates, @Nullable String place,
        DeclineReason reason) implements EventToParticipate {

    /**
     * コンストラクタ
     *
     * @param name
     *            イベント名（必須）
     * @param declinedDates
     *            不参加確定した日程リスト（必須、1つ以上）
     * @param place
     *            会場（nullable）
     * @param reason
     *            不参加理由（必須）
     * @throws IllegalArgumentException
     *             イベント名がnull、declinedDatesが空、またはreasonがnullの場合
     */
    public DeclinedEvent {
        Policies.multiple(
                Policy.<EventName>of(
                        Objects::nonNull,
                        () -> new ErrorResult(
                                "name",
                                "Event name cannot be null",
                                "EVENT_NAME_REQUIRED"))
                        .verify(name, Function.identity()),
                Policy.<List<BusinessDate>>of(
                        CollectionUtils::isNotEmpty,
                        () -> new ErrorResult(
                                "declinedDates",
                                "Declined event must have at least one declined date",
                                "DECLINED_DATES_REQUIRED"))
                        .verify(declinedDates, Function.identity()),
                Policy.<DeclineReason>of(
                        Objects::nonNull,
                        () -> new ErrorResult(
                                "reason",
                                "Decline reason cannot be null",
                                "DECLINE_REASON_REQUIRED"))
                        .verify(reason, Function.identity()))
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
        declinedDates = Collections.unmodifiableList(declinedDates);
    }

    /**
     * 単一日程で生成
     *
     * @param name
     *            イベント名
     * @param date
     *            不参加日
     * @param reason
     *            不参加理由
     * @return DeclinedEvent
     */
    public static DeclinedEvent of(
            String name,
            BusinessDate date,
            DeclineReason reason) {
        return new DeclinedEvent(
                new EventName(name),
                List.of(date),
                null,
                reason);
    }

    /**
     * 単一日程で会場情報付きで生成
     *
     * @param name
     *            イベント名
     * @param date
     *            不参加日
     * @param place
     *            会場
     * @param reason
     *            不参加理由
     * @return DeclinedEvent
     */
    public static DeclinedEvent of(
            String name,
            BusinessDate date,
            String place,
            DeclineReason reason) {
        return new DeclinedEvent(
                new EventName(name),
                List.of(date),
                place,
                reason);
    }

    /**
     * 複数日程で生成
     *
     * @param name
     *            イベント名
     * @param declinedDates
     *            不参加日程リスト
     * @param place
     *            会場
     * @param reason
     *            不参加理由
     * @return DeclinedEvent
     */
    public static DeclinedEvent of(
            String name,
            List<BusinessDate> declinedDates,
            String place,
            DeclineReason reason) {
        return new DeclinedEvent(
                new EventName(name),
                declinedDates,
                place,
                reason);
    }

    /**
     * TentativeEventから不参加イベントに変換
     *
     * @param tentative
     *            未確定イベント
     * @param reason
     *            不参加理由
     * @return DeclinedEvent
     */
    public static DeclinedEvent fromTentative(TentativeEvent tentative, DeclineReason reason) {
        final var dates = tentative.tentativeDates();
        Policy.<List<BusinessDate>>of(
                CollectionUtils::isNotEmpty,
                () -> new ErrorResult(
                        "dates",
                        "Cannot decline event without dates",
                        "DECLINE_DATES_REQUIRED"))
                .verify(dates, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
        return new DeclinedEvent(
                tentative.name(),
                dates,
                null,
                reason);
    }

    /**
     * AppliedEventから不参加イベントに変換
     *
     * @param applied
     *            申込済みイベント
     * @param reason
     *            不参加理由
     * @return DeclinedEvent
     */
    public static DeclinedEvent fromApplied(AppliedEvent applied, DeclineReason reason) {
        final var dates = applied.tentativeDates();
        Policy.<List<BusinessDate>>of(
                CollectionUtils::isNotEmpty,
                () -> new ErrorResult(
                        "dates",
                        "Cannot decline event without dates",
                        "DECLINE_DATES_REQUIRED"))
                .verify(dates, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
        return new DeclinedEvent(
                applied.name(),
                dates,
                null,
                reason);
    }

    @Override
    public boolean equivalentTo(EventToParticipate other) {
        return Optional.ofNullable(other).map(asType(DeclinedEvent.class))
                .filter(
                        and(
                                having(DeclinedEvent::name).that(this.name::equivalentTo),
                                having(DeclinedEvent::declinedDates).thatEqualsTo(this.declinedDates),
                                having(DeclinedEvent::place).thatEqualsTo(this.place),
                                having(DeclinedEvent::reason).thatEqualsTo(this.reason)))
                .isPresent();
    }
}
