package com.abservice.infrastructure.persistence.entity;

import com.abservice.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * イベントエンティティ
 * <p>
 * テーブル: event
 * </p>
 */
@Entity
@Table(name = "event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "date")
    private LocalDate date;

    @Column(name = "place", length = 255)
    private String place;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
