package com.abservice.domain.repository.event;

import com.abservice.domain.model.aggregate.event.Event;
import com.abservice.domain.model.vo.event.EventName;
import com.abservice.domain.repository.Repository;
import io.smallrye.mutiny.Uni;
import java.time.LocalDate;

/**
 * イベントリポジトリ
 *
 * <p>
 * Event集約の永続化と取得を担当します。
 * </p>
 */
public interface EventRepository extends Repository<Event, Event.Id> {

    /**
     * イベント名でイベントを検索
     *
     * @param name
     *            イベント名
     * @return 該当するイベントのリスト
     */
    Uni<java.util.List<Event>> findByName(EventName name);

    /**
     * 開催日でイベントを検索
     *
     * @param date
     *            開催日
     * @return 該当するイベントのリスト
     */
    Uni<java.util.List<Event>> findByDate(LocalDate date);

    /**
     * 開催日の範囲でイベントを検索
     *
     * @param startDate
     *            開始日
     * @param endDate
     *            終了日
     * @return 該当するイベントのリスト
     */
    Uni<java.util.List<Event>> findByDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * 会場でイベントを検索（部分一致）
     *
     * @param placeKeyword
     *            会場キーワード
     * @return 該当するイベントのリスト
     */
    Uni<java.util.List<Event>> findByPlaceContaining(String placeKeyword);

    /**
     * 年でイベントを検索
     *
     * @param year
     *            年
     * @return 該当するイベントのリスト
     */
    Uni<java.util.List<Event>> findByYear(int year);
}
