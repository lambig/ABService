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
 * 申込中イベント Value Object
 *
 * <p>
 * イベント参加申込中の状態を表します。 申込フォームの記入中や、申込手続きを進めている段階です。
 * </p>
 *
 * <p>
 * 使用例：
 * </p>
 * <ul>
 * <li>「コミックマーケット105」の申込フォームを記入中（2日間開催）</li>
 * <li>「M3-2025春」の申込手続きを進行中</li>
 * </ul>
 *
 * @param name
 *            イベント名（必須）
 * @param tentativeDates
 *            暫定開催日リスト（空可、未定の場合は空リスト）
 */
public record ApplyingEvent(EventName name, List<BusinessDate> tentativeDates) implements TentativeEvent {

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
    public ApplyingEvent {
        Policy.<EventName>of(Objects::nonNull,
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
     * @return ApplyingEvent
     */
    public static ApplyingEvent of(String name) {
        return new ApplyingEvent(new EventName(name), List.of());
    }

    /**
     * イベント名と単一日程で生成
     *
     * @param name
     *            イベント名
     * @param tentativeDate
     *            暫定開催日
     * @return ApplyingEvent
     */
    public static ApplyingEvent of(String name, BusinessDate tentativeDate) {
        return new ApplyingEvent(new EventName(name), List.of(tentativeDate));
    }

    /**
     * イベント名と複数日程で生成
     *
     * @param name
     *            イベント名
     * @param tentativeDates
     *            暫定開催日リスト
     * @return ApplyingEvent
     */
    public static ApplyingEvent of(String name, List<BusinessDate> tentativeDates) {
        return new ApplyingEvent(new EventName(name), tentativeDates);
    }

    /**
     * ConsideringEventから申込中イベントに変換
     *
     * @param considering
     *            検討中イベント
     * @return ApplyingEvent
     */
    public static ApplyingEvent from(ConsideringEvent considering) {
        return new ApplyingEvent(considering.name(), considering.tentativeDates());
    }

    /**
     * 申込完了へ状態遷移
     *
     * @return AppliedEvent
     */
    public AppliedEvent completeApplication() {
        return AppliedEvent.from(this);
    }

    @Override
    public boolean equivalentTo(EventToParticipate other) {
        return Optional.ofNullable(other).map(asType(ApplyingEvent.class))
                .filter(and(having(ApplyingEvent::name).that(this.name::equivalentTo),
                        having(ApplyingEvent::tentativeDates).thatEqualsTo(this.tentativeDates)))
                .isPresent();
    }
}
