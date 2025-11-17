package com.blackjack.blackjack.dto;

import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.Player;
import jakarta.servlet.http.HttpSession;

import java.util.Optional;
import java.util.UUID;

public record SessionContext(
    UUID clientId,
    Player player,
    HttpSession session,
    Optional<GameState> gameStateOptional
) {
}
