package com.blackjack.blackjack.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.SuperBuilder;

// A <T> jelzi, hogy ez az osztály egy generikus típust használ a tartalomhoz.
@Value
@Builder(toBuilder = true)
@AllArgsConstructor
public class ApiResponse<T> {
    String status;
    @JsonProperty("game_state")
    T gameState;
    @JsonProperty("game_state_hint")
    String gameStateHint;
    @JsonProperty("current_tokens")
    int currentTokens;
}
