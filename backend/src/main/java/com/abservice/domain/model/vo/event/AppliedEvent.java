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
 * 申込済みイベント Value Object
 *
 * <p>
 * イベント参加申込が完了し、抽選結果待ちの状態を表します。
 * </p>
 *
 * <p>
 * 使用例：
 * </p>
 * <ul>
 * <li>「コミックマーケット105」の申込を完了、抽選結果待ち（2日間開催）</li>
 * <li>「M3-2025春」の申込済み、当落発表待ち</li>
 * </ul>
 *
 * @param name
 *            イベント名（必須）
 * @param tentativeDates
 *            暫定開催日リスト（空可、未定の場合は空リスト）
 */
public record AppliedEvent(EventName name, List<BusinessDate> tentativeDates) implements TentativeEvent {

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
    public AppliedEvent {
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
     * @return AppliedEvent
     */
    public static AppliedEvent of(String name) {
        return new AppliedEvent(new EventName(name), List.of());
    }

    /**
     * イベント名と単一日程で生成
     *
     * @param name
     *            イベント名
     * @param tentativeDate
     *            暫定開催日
     * @return AppliedEvent
     */
    public static AppliedEvent of(String name, BusinessDate tentativeDate) {
        return new AppliedEvent(new EventName(name), List.of(tentativeDate));
    }

    /**
     * イベント名と複数日程で生成
     *
     * @param name
     *            イベント名
     * @param tentativeDates
     *            暫定開催日リスト
     * @return AppliedEvent
     */
    public static AppliedEvent of(String name, List<BusinessDate> tentativeDates) {
        return new AppliedEvent(new EventName(name), tentativeDates);
    }

    /**
     * ApplyingEventから変換
     *
     * @param applying
     *            申込中イベント
     * @return AppliedEvent
     */
    public static AppliedEvent from(ApplyingEvent applying) {
        return new AppliedEvent(applying.name(), applying.tentativeDates());
    }

    /**
     * ConsideringEventから直接変換（申込中をスキップ）
     *
     * @param considering
     *            検討中イベント
     * @return AppliedEvent
     */
    public static AppliedEvent from(ConsideringEvent considering) {
        return new AppliedEvent(considering.name(), considering.tentativeDates());
    }

    @Override
    public boolean equivalentTo(EventToParticipate other) {
        return Optional.ofNullable(other).map(asType(AppliedEvent.class))
                .filter(
                        and(
                                having(AppliedEvent::name).that(this.name::equivalentTo),
                                having(AppliedEvent::tentativeDates).thatEqualsTo(this.tentativeDates)))
                .isPresent();
    }
}
