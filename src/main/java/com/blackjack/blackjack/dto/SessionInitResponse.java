package com.blackjack.blackjack.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class SessionInitResponse {
    private String status;
    private String message;
    private int tokens;
    private GameStateInit gameState;
    private String gameStateHint;
    private UUID clientId;
}
