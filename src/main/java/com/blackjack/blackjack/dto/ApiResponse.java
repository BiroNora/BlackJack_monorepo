package com.blackjack.blackjack.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder(toBuilder = true)
public record ApiResponse<T>(String status, @JsonProperty("game_state") T gameState,
                             @JsonProperty("game_state_hint") String gameStateHint,
                             @JsonProperty("current_tokens") int currentTokens) {
}
