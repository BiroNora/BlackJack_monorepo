package com.blackjack.blackjack.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

// A <T> jelzi, hogy ez az osztály egy generikus típust használ a tartalomhoz.
@Data
@SuperBuilder // Ezt használjuk Builder öröklődéshez!
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private String status;
    @JsonProperty("game_state")
    private T gameState;
    @JsonProperty("game_state_hint")
    private String gameStateHint;
    @JsonProperty("current_tokens")
    private int currentTokens;
}
