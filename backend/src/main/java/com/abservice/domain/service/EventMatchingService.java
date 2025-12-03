package com.abservice.domain.service;

import com.abservice.domain.model.vo.common.EventReleasedAt;
import com.abservice.domain.model.vo.event.ConfirmedEvent;
import com.abservice.domain.model.vo.event.EventToParticipate;
import com.abservice.domain.model.vo.event.TentativeEvent;
import jakarta.enterprise.context.ApplicationScoped;

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
        if (toParticipate == null || releasedAt == null) {
            return false;
        }

        // イベント名の一致は必須
        if (!toParticipate.name().equivalentTo(releasedAt.name())) {
            return false;
        }

        // 未確定イベントの場合はイベント名のみで判断
        if (toParticipate instanceof TentativeEvent) {
            return true;
        }

        // 確定イベントの場合は日付・スペースも厳密に比較
        if (toParticipate instanceof ConfirmedEvent confirmed) {
            return confirmed.dateAndSpaces().equals(releasedAt.dateAndSpaces());
        }

        return false;
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
        if (toParticipate == null || releasedAt == null) {
            return false;
        }

        // イベント名の一致は必須
        if (!toParticipate.name().equivalentTo(releasedAt.name())) {
            return false;
        }

        // 未確定イベントの場合
        if (toParticipate instanceof TentativeEvent tentative) {
            // 暫定日付がない場合は名前のみで一致とみなす
            if (tentative.tentativeDates().isEmpty()) {
                return true;
            }
            // 暫定日付がある場合は、releasedAtの日付リストにいずれかの日付が含まれるか確認
            var releasedDates = releasedAt.dateAndSpaces().stream().map(ds -> ds.date()).toList();
            return tentative.tentativeDates().stream().anyMatch(releasedDates::contains);
        }

        // 確定イベントの場合は日付のみを比較（スペース番号は無視）
        if (toParticipate instanceof ConfirmedEvent confirmed) {
            var participateDates = confirmed.dateAndSpaces().stream().map(ds -> ds.date()).toList();
            var releasedDates = releasedAt.dateAndSpaces().stream().map(ds -> ds.date()).toList();
            return participateDates.equals(releasedDates);
        }

        return false;
    }
}
