package com.blackjack.blackjack.dto.init;

import com.blackjack.blackjack.model.PhaseState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Builder(toBuilder = true)
public record InitializationResponse(String status, String message, GameStateInit gameState, String gameStateHint,
                                     int tokens, Integer totalInitialCards) {
    // --- Belső DTO a gameState-nek ---
    @Builder(toBuilder = true)
    public record GameStateInit(
        int deckLen,
        PhaseState targetPhase
    ) {}
}
