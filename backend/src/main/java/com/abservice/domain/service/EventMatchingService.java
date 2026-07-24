package com.abservice.domain.service;

import static com.abservice.lib.Optionals.both;
import static io.github.lambig.funcifextension.predicate.Predicates.or;

import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.EventReleasedAt;
import com.abservice.domain.model.vo.event.ConfirmedEvent;
import com.abservice.domain.model.vo.event.EventToParticipate;
import com.abservice.domain.model.vo.event.TentativeEvent;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * イベント照合ドメインサービス
 *
 * <p>
 * 参加予定イベント（EventToParticipate）とアルバム頒布実績イベント（EventReleasedAt）の
 * 同一性判断を行うドメインサービスです。
 * </p>
 *
 * <h2>業務ルール</h2>
 * <ul>
 * <li>EventToParticipateの絶対数はEventReleasedAtより多い（全ての参加予定を管理）</li>
 * <li>EventReleasedAtはEventToParticipateに必ずしも対応しない（委託参加のケース）</li>
 * <li>未確定イベント（TentativeEvent）はイベント名のみで照合</li>
 * <li>確定イベント（ConfirmedEvent）は名前・日付・スペースで厳密に照合</li>
 * </ul>
 */
@ApplicationScoped
public class EventMatchingService implements DomainService {

    /**
     * EventToParticipateとEventReleasedAtが同一イベントかを判定
     *
     * <p>
     * 判定ルール：
     * </p>
     * <ul>
     * <li>イベント名が一致していることが前提</li>
     * <li>未確定イベントの場合：イベント名のみで判定（日付は考慮しない）</li>
     * <li>確定イベントの場合：イベント名・日付・スペース番号の完全一致で判定</li>
     * </ul>
     *
     * @param toParticipate
     *            参加予定イベント
     * @param releasedAt
     *            頒布実績イベント
     * @return 同一イベントと判定される場合true
     */
    public boolean isSameEvent(EventToParticipate toParticipate, EventReleasedAt releasedAt) {
        return both(toParticipate, releasedAt)
                .filter(pair -> pair.a().name().equivalentTo(pair.b().name()))
                .map(pair -> matchesEventDetails(pair.a(), pair.b()))
                .orElse(false);
    }

    private boolean matchesEventDetails(EventToParticipate toParticipate, EventReleasedAt releasedAt) {
        return switch (toParticipate) {
            case TentativeEvent ignored -> true;
            case ConfirmedEvent confirmed -> confirmed.dateAndSpaces().equals(releasedAt.dateAndSpaces());
            default -> false;
        };
    }

    /**
     * EventToParticipateがEventReleasedAtと部分的に一致するかを判定
     *
     * <p>
     * より緩い判定条件で、イベント名と日付（スペースは無視）のみで判定します。 未確定イベントで暫定日付が設定されている場合に使用します。
     * </p>
     *
     * @param toParticipate
     *            参加予定イベント
     * @param releasedAt
     *            頒布実績イベント
     * @return イベント名と日付が一致する場合true
     */
    public boolean matchesEventNameAndDate(EventToParticipate toParticipate, EventReleasedAt releasedAt) {
        return both(toParticipate, releasedAt)
                .filter(pair -> pair.a().name().equivalentTo(pair.b().name()))
                .map(pair -> matchesDateDetails(pair.a(), pair.b()))
                .orElse(false);
    }

    private boolean matchesDateDetails(EventToParticipate toParticipate, EventReleasedAt releasedAt) {
        return switch (toParticipate) {
            case TentativeEvent tentative -> matchesTentativeDates(tentative, releasedAt);
            case ConfirmedEvent confirmed -> matchesConfirmedDates(confirmed, releasedAt);
            default -> false;
        };
    }

    private boolean matchesTentativeDates(TentativeEvent tentative, EventReleasedAt releasedAt) {
        final var releasedDates = releasedAt.dateAndSpaces().stream().map(ds -> ds.date()).toList();
        return or(
                (List<BusinessDate> dates) -> dates.isEmpty(),
                dates -> dates.stream().anyMatch(releasedDates::contains)).test(tentative.tentativeDates());
    }

    private boolean matchesConfirmedDates(ConfirmedEvent confirmed, EventReleasedAt releasedAt) {
        return confirmed.dateAndSpaces().stream().map(ds -> ds.date()).toList()
                .equals(releasedAt.dateAndSpaces().stream().map(ds -> ds.date()).toList());
    }
}
