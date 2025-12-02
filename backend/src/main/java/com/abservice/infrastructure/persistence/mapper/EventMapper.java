package com.abservice.infrastructure.persistence.mapper;

import com.abservice.domain.model.aggregate.event.Event;
import com.abservice.domain.model.vo.event.EventName;
import com.abservice.infrastructure.persistence.entity.EventEntity;

/**
 * Event Mapper
 *
 * <p>
 * EventドメインモデルとEventEntityの相互変換を担当します。
 * </p>
 */
public class EventMapper {

    private EventMapper() {
        // ユーティリティクラス
    }

    /**
     * EntityからDomainモデルへ変換
     *
     * @param entity
     *            EventEntity
     * @return Event
     */
    public static Event toDomain(EventEntity entity) {
        if (entity == null) {
            return null;
        }

        return new Event(new Event.Id(entity.getDomainId()), new EventName(entity.getName()), entity.getDate(),
                entity.getPlace(), entity.getNote());
    }

    /**
     * DomainモデルからEntityへ変換
     *
     * @param event
     *            Event
     * @return EventEntity
     */
    public static EventEntity toEntity(Event event) {
        if (event == null) {
            return null;
        }

        var eventEntity = new EventEntity();
        eventEntity.setDomainId(event.id().value());
        eventEntity.setName(event.name().value());
        eventEntity.setDate(event.date());
        eventEntity.setPlace(event.place());
        eventEntity.setNote(event.note());

        return eventEntity;
    }
}
