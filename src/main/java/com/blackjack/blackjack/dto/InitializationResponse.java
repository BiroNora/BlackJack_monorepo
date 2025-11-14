package com.blackjack.blackjack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitializationResponse {
    private String status;
    private String message;
    private GameStateInit gameState;
    private String gameStateHint;
    private int tokens;

    // --- Belső DTO a gameState-nek ---
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GameStateInit {
        private int deckLen;
    }
}
