package com.abservice.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Read projection; transitions are serialized in the dedicated store. */
@Entity
@Table(name = "listening_authorization")
@Getter
@NoArgsConstructor
public class ListeningAuthorizationTableRecord {
    @Id
    @Column(name = "request_id")
    private UUID requestId;
    private String label;
    private String state;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "valid_days")
    private int validDays;
}
