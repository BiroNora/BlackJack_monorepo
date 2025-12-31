package com.blackjack.blackjack.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "players")
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor // JPA miatt
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Player {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.AUTO)
    @Column(name = "id", unique = true, nullable = false)
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID id;

    @Column(name = "clientId", unique = true, nullable = false)
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID clientId;

    @Column(name = "tokens", nullable = false)
    private int tokens;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "current_game_state", columnDefinition = "jsonb")
    private GameState currentGameState;

    @UpdateTimestamp
    @Column(name = "last_activity", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime lastActivity;

    @Column(name = "idempotency_key", unique = true, nullable = false)
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID idempotencyKey;
}
