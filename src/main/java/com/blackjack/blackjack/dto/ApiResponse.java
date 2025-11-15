package com.blackjack.blackjack.dto;

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
    private String message;
    private T gameState;
    private String gameStateHint;
    private int tokens;
}
