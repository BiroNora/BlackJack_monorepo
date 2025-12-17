package com.blackjack.blackjack.dto.init;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Builder(toBuilder = true)
public record InitializationResponse(String status, String message, GameStateInit gameState, String gameStateHint,
                                     int tokens) {
    // --- Belső DTO a gameState-nek ---
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GameStateInit {
        private int deckLen;
    }
}
