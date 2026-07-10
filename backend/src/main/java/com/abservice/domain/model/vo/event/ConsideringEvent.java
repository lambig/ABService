package com.abservice.domain.model.vo.event;

import static com.abservice.domain.model.DomainObject.asType;
import static io.github.lambig.funcifextension.predicate.By.having;
import static io.github.lambig.funcifextension.predicate.Predicates.and;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.lib.ErrorResult;
import org.apache.commons.collections4.ListUtils;

/**
 * 検討中イベント Value Object
 *
 * <p>
 * イベント参加を検討中の状態を表します。 まだ申込を開始していないため、先方に迷惑をかけないようイベント名を匿名化する選択肢があります。
 * </p>
 *
 * <p>
 * 使用例：
 * </p>
 * <ul>
 * <li>「コミックマーケット105」への参加を検討中（2日間開催）</li>
 * <li>「M3-2025春」の申込を検討しているが未確定</li>
 * </ul>
 *
 * @param name
 *            イベント名（必須）
 * @param tentativeDates
 *            暫定開催日リスト（空可、未定の場合は空リスト）
 */
public record ConsideringEvent(EventName name, List<BusinessDate> tentativeDates) implements TentativeEvent {

    /**
     * コンストラクタ
     *
     * @param name
     *            イベント名（必須）
     * @param tentativeDates
     *            暫定開催日リスト（空可）
     * @throws IllegalArgumentException
     *             イベント名がnullの場合
     */
    public ConsideringEvent {
        Policy.<EventName>of(
                Objects::nonNull,
                () -> new ErrorResult("name", "Event name cannot be null", "EVENT_NAME_REQUIRED"))
                .verify(name, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
        tentativeDates = Collections.unmodifiableList(ListUtils.emptyIfNull(tentativeDates));
    }

    /**
     * イベント名のみで生成（日程未定）
     *
     * @param name
     *            イベント名
     * @return ConsideringEvent
     */
    public static ConsideringEvent of(String name) {
        return new ConsideringEvent(new EventName(name), List.of());
    }

    /**
     * イベント名と単一日程で生成
     *
     * @param name
     *            イベント名
     * @param tentativeDate
     *            暫定開催日
     * @return ConsideringEvent
     */
    public static ConsideringEvent of(String name, BusinessDate tentativeDate) {
        return new ConsideringEvent(new EventName(name), List.of(tentativeDate));
    }

    /**
     * イベント名と複数日程で生成
     *
     * @param name
     *            イベント名
     * @param tentativeDates
     *            暫定開催日リスト
     * @return ConsideringEvent
     */
    public static ConsideringEvent of(String name, List<BusinessDate> tentativeDates) {
        return new ConsideringEvent(new EventName(name), tentativeDates);
    }

    /**
     * 申込中へ状態遷移
     *
     * @return ApplyingEvent
     */
    public ApplyingEvent startApplying() {
        return ApplyingEvent.from(this);
    }

    @Override
    public boolean equivalentTo(EventToParticipate other) {
        return Optional.ofNullable(other).map(asType(ConsideringEvent.class))
                .filter(
                        and(
                                having(ConsideringEvent::name).that(this.name::equivalentTo),
                                having(ConsideringEvent::tentativeDates).thatEqualsTo(this.tentativeDates)))
                .isPresent();
    }
}
