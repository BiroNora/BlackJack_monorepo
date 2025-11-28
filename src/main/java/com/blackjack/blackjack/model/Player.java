package com.blackjack.blackjack.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "players")
@Data
@NoArgsConstructor
public class Player {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.AUTO)
    @Column(name = "id", unique = true, nullable = false)
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID id;

    @Column(name = "client_id", unique = true, nullable = false)
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID clientId;

    @Column(name = "session_id", unique = true, nullable = false)
    private String sessionId;

    @Column(name = "tokens", nullable = false)
    private int tokens;

    @UpdateTimestamp
    @Column(name = "last_activity", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime lastActivity;

    @Column(name = "idempotency_key", unique = true, nullable = false)
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID idempotencyKey;

    public Player(int initialTokens) {
        this.tokens = initialTokens;
    }

    public Player(UUID clientId, int initialTokens) {
        this.clientId = clientId;
        this.tokens = initialTokens;
    }
}
