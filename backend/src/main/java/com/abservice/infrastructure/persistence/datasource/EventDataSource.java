package com.abservice.infrastructure.persistence.datasource;

import com.abservice.infrastructure.persistence.entity.EventEntity;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.hibernate.reactive.mutiny.Mutiny;

import java.time.LocalDate;
import java.util.List;

/**
 * Event DataSource (DAO)
 *
 * <p>
 * Panacheを使用したイベントデータアクセス層。
 * </p>
 */
@ApplicationScoped
public class EventDataSource implements PanacheRepositoryBase<EventEntity, Long> {

    private final Mutiny.SessionFactory sessionFactory;

    public EventDataSource(Mutiny.SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    /**
     * イベント名でイベントを検索
     *
     * @param name
     *            イベント名
     * @return 該当するイベントのリスト
     */
    public Uni<List<EventEntity>> findByName(String name) {
        return list("name", name);
    }

    /**
     * 開催日でイベントを検索
     *
     * @param date
     *            開催日
     * @return 該当するイベントのリスト
     */
    public Uni<List<EventEntity>> findByDate(LocalDate date) {
        return list("date", date);
    }

    /**
     * 開催日の範囲でイベントを検索
     *
     * @param startDate
     *            開始日
     * @param endDate
     *            終了日
     * @return 該当するイベントのリスト
     */
    public Uni<List<EventEntity>> findByDateBetween(LocalDate startDate, LocalDate endDate) {
        return sessionFactory.withSession(session -> session
                .createQuery("SELECT e FROM EventEntity e WHERE e.date >= :startDate AND e.date <= :endDate",
                        EventEntity.class)
                .setParameter("startDate", startDate).setParameter("endDate", endDate).getResultList());
    }

    /**
     * 会場でイベントを検索（部分一致）
     *
     * @param placeKeyword
     *            会場キーワード
     * @return 該当するイベントのリスト
     */
    public Uni<List<EventEntity>> findByPlaceContaining(String placeKeyword) {
        return sessionFactory.withSession(session -> session
                .createQuery("SELECT e FROM EventEntity e WHERE e.place LIKE :keyword", EventEntity.class)
                .setParameter("keyword", "%" + placeKeyword + "%").getResultList());
    }

    /**
     * 年でイベントを検索
     *
     * @param year
     *            年
     * @return 該当するイベントのリスト
     */
    public Uni<List<EventEntity>> findByYear(int year) {
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);
        return findByDateBetween(startDate, endDate);
    }

    /**
     * イベントIDで削除
     *
     * @param id
     *            イベントID
     * @return 削除された場合true
     */
    public Uni<Boolean> deleteByEventId(String domainId) {
        return delete("domainId", domainId).onItem().transform(count -> count > 0);
    }

    /**
     * イベントIDでイベントが存在するか確認
     *
     * @param id
     *            イベントID
     * @return 存在する場合true
     */
    public Uni<Boolean> existsByEventId(String domainId) {
        return count("domainId", domainId).onItem().transform(count -> count > 0);
    }
}
