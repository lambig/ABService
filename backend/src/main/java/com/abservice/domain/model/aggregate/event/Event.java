package com.abservice.domain.model.aggregate.event;

import java.time.LocalDate;

import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.aggregate.Aggregate;
import com.abservice.domain.model.vo.event.EventName;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.With;

/**
 * イベント集約
 *
 * <p>
 * コミケ、M3、ライブなどのイベント情報を管理します。
 * </p>
 * <p>
 * 現在は小さいエンティティですが、将来的にイベント詳細情報が追加される場合は、 完全な集約ルートとして拡張されます。
 * </p>
 */
@With(AccessLevel.PRIVATE)
@Getter
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Event implements Aggregate<Event, Event.Id> {
    @EqualsAndHashCode.Include
    private final Id id;
    private final EventName name;
    private final LocalDate date;
    private final String place;
    private final String note;

    /**
     * イベント名を変更
     *
     * @param newName
     *            新しいイベント名
     * @return 更新されたEvent
     */
    public Event changeName(EventName newName) {
        if (newName == null) {
            throw new IllegalArgumentException("Event name cannot be null");
        }
        return withName(newName);
    }

    /**
     * 開催日を変更
     *
     * @param newDate
     *            新しい開催日
     * @return 更新されたEvent
     */
    public Event changeDate(LocalDate newDate) {
        return withDate(newDate);
    }

    /**
     * 会場を変更
     *
     * @param newPlace
     *            新しい会場
     * @return 更新されたEvent
     */
    public Event changePlace(String newPlace) {
        return withPlace(newPlace);
    }

    /**
     * 補足情報を変更
     *
     * @param newNote
     *            新しい補足情報
     * @return 更新されたEvent
     */
    public Event changeNote(String newNote) {
        return withNote(newNote);
    }

    @Override
    public Id id() {
        return id;
    }

    /**
     * Event ID型
     *
     * @param value
     *            ID値（UUIDv7形式の文字列）
     */
    public record Id(String value) implements EntityId<Event> {
        public Id {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Event ID cannot be blank");
            }
            if (!EntityId.isValidUuid(value)) {
                throw new IllegalArgumentException("Event ID must be a valid UUID: " + value);
            }
        }

        /**
         * UUIDv7を生成してEvent.Idを作成
         */
        public static Id generate() {
            return new Id(EntityId.generateUuidV7());
        }

        /**
         * 文字列からEvent.Idを生成
         */
        public static Id of(String value) {
            return new Id(value);
        }
    }
}
