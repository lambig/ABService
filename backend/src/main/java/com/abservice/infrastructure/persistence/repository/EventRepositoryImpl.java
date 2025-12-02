package com.abservice.infrastructure.persistence.repository;

import com.abservice.domain.model.aggregate.event.Event;
import com.abservice.domain.model.vo.event.EventName;
import com.abservice.domain.repository.event.EventRepository;
import com.abservice.infrastructure.persistence.datasource.EventDataSource;
import com.abservice.infrastructure.persistence.mapper.EventMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * EventRepository実装
 *
 * <p>
 * Panacheを使用した非同期リポジトリ実装。
 * </p>
 */
@ApplicationScoped
public class EventRepositoryImpl implements EventRepository {

    private final EventDataSource dataSource;

    public EventRepositoryImpl(EventDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Uni<Event> save(Event aggregate) {
        if (aggregate == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Event cannot be null"));
        }

        var entity = EventMapper.toEntity(aggregate);

        return dataSource.existsByEventId(entity.getEventId())
                .flatMap(exists -> {
                    if (exists) {
                        return dataSource.findById(entity.getEventId())
                                .flatMap(existingEntity -> {
                                    existingEntity.setName(entity.getName());
                                    existingEntity.setDate(entity.getDate());
                                    existingEntity.setPlace(entity.getPlace());
                                    existingEntity.setNote(entity.getNote());
                                    return dataSource.persistAndFlush(existingEntity);
                                });
                    } else {
                        return dataSource.persistAndFlush(entity);
                    }
                })
                .map(EventMapper::toDomain);
    }

    @Override
    public Uni<List<Event>> saveAll(Iterable<Event> aggregates) {
        if (aggregates == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Aggregates cannot be null"));
        }

        var unis = java.util.stream.StreamSupport.stream(aggregates.spliterator(), false)
                .map(this::save)
                .collect(Collectors.toList());

        return Uni.join().all(unis).andFailFast();
    }

    @Override
    public Uni<Event> findById(Event.Id id) {
        if (id == null) {
            return Uni.createFrom().nullItem();
        }

        return dataSource.findById(id.value())
                .map(EventMapper::toDomain);
    }

    @Override
    public Uni<List<Event>> findAllById(Iterable<Event.Id> ids) {
        if (ids == null) {
            return Uni.createFrom().item(List.of());
        }

        var unis = java.util.stream.StreamSupport.stream(ids.spliterator(), false)
                .map(this::findById)
                .collect(Collectors.toList());

        return Uni.join().all(unis).andFailFast()
                .map(list -> list.stream()
                        .filter(event -> event != null)
                        .collect(Collectors.toList()));
    }

    @Override
    public Uni<List<Event>> findAll() {
        return dataSource.listAll()
                .map(entities -> entities.stream()
                        .map(EventMapper::toDomain)
                        .collect(Collectors.toList()));
    }

    @Override
    public Uni<Void> delete(Event aggregate) {
        if (aggregate == null) {
            return Uni.createFrom().voidItem();
        }
        return deleteById(aggregate.id());
    }

    @Override
    public Uni<Void> deleteAll(Iterable<Event> aggregates) {
        if (aggregates == null) {
            return Uni.createFrom().voidItem();
        }

        var unis = java.util.stream.StreamSupport.stream(aggregates.spliterator(), false)
                .map(this::delete)
                .collect(Collectors.toList());

        return Uni.join().all(unis).andFailFast()
                .replaceWithVoid();
    }

    @Override
    public Uni<Void> deleteById(Event.Id id) {
        if (id == null) {
            return Uni.createFrom().voidItem();
        }

        return dataSource.deleteByEventId(id.value())
                .replaceWithVoid();
    }

    @Override
    public Uni<Void> deleteAllById(Iterable<Event.Id> ids) {
        if (ids == null) {
            return Uni.createFrom().voidItem();
        }

        var unis = java.util.stream.StreamSupport.stream(ids.spliterator(), false)
                .map(this::deleteById)
                .collect(Collectors.toList());

        return Uni.join().all(unis).andFailFast()
                .replaceWithVoid();
    }

    @Override
    public Uni<Boolean> existsById(Event.Id id) {
        if (id == null) {
            return Uni.createFrom().item(false);
        }

        return dataSource.existsByEventId(id.value());
    }

    @Override
    public Uni<Long> count() {
        return dataSource.count();
    }

    // カスタムメソッド

    @Override
    public Uni<List<Event>> findByName(EventName name) {
        if (name == null) {
            return Uni.createFrom().item(List.of());
        }

        return dataSource.findByName(name.value())
                .map(entities -> entities.stream()
                        .map(EventMapper::toDomain)
                        .collect(Collectors.toList()));
    }

    @Override
    public Uni<List<Event>> findByDate(LocalDate date) {
        if (date == null) {
            return Uni.createFrom().item(List.of());
        }

        return dataSource.findByDate(date)
                .map(entities -> entities.stream()
                        .map(EventMapper::toDomain)
                        .collect(Collectors.toList()));
    }

    @Override
    public Uni<List<Event>> findByDateBetween(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return Uni.createFrom().item(List.of());
        }

        return dataSource.findByDateBetween(startDate, endDate)
                .map(entities -> entities.stream()
                        .map(EventMapper::toDomain)
                        .collect(Collectors.toList()));
    }

    @Override
    public Uni<List<Event>> findByPlaceContaining(String placeKeyword) {
        if (placeKeyword == null) {
            return Uni.createFrom().item(List.of());
        }

        return dataSource.findByPlaceContaining(placeKeyword)
                .map(entities -> entities.stream()
                        .map(EventMapper::toDomain)
                        .collect(Collectors.toList()));
    }

    @Override
    public Uni<List<Event>> findByYear(int year) {
        return dataSource.findByYear(year)
                .map(entities -> entities.stream()
                        .map(EventMapper::toDomain)
                        .collect(Collectors.toList()));
    }
}
