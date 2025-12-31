package com.blackjack.blackjack.service;

import com.blackjack.blackjack.dto.GameContext;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.repository.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@Service
public class AuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);
    private final PlayerRepository playerRepository;
    private final GameStateService gameStateService;

    public AuthenticationService(PlayerRepository playerRepository, GameStateService gameStateService) {
        this.playerRepository = playerRepository;
        this.gameStateService = gameStateService;
    }

    /**
     * Session mentes validálás a clientId alapján.
     */
    public GameContext validateAndGetContext(UUID clientId) {
        if (clientId == null) {
            logger.warn("HIBA: Nincs clientId a kérésben.");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FORBIDDEN: Missing Client ID");
        }

        // 1. Játékos keresése
        Player player = playerRepository.findByClientId(clientId)
            .orElseThrow(() -> {
                logger.warn("A Client ID nem létezik: {}", clientId);
                return new ResponseStatusException(HttpStatus.NOT_FOUND, "PLAYER_NOT_FOUND");
            });

        // 2. GameState lekérése (vagy Redis-ből, vagy ahogy nálad van)
        Optional<GameState> gameStateOptional = gameStateService.getGameState(clientId);

        // A SessionContext-ben a session helyére null-t küldünk, mert nincs rá szükség
        return new GameContext(clientId, player, gameStateOptional);
    }
}
