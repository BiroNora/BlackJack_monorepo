package com.blackjack.blackjack.dto.init;

import lombok.*;


@Value
@Builder(toBuilder = true)
@AllArgsConstructor
public class InitializationResponse {
    String status;
    String message;
    GameStateInit gameState;
    String gameStateHint;
    int tokens;

    // --- Belső DTO a gameState-nek ---
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GameStateInit {
        private int deckLen;
    }
}
