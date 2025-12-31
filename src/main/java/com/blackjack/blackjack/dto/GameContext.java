package com.blackjack.blackjack.dto;

import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * A játék aktuális környezetét összefogó objektum.
 */
public record GameContext(
    UUID clientId,
    Player player,
    Optional<GameState> gameStateOptional
) {
}
