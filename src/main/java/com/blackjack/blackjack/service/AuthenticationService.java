package com.blackjack.blackjack.service;

import com.blackjack.blackjack.dto.SessionContext;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.repository.PlayerRepository;
import jakarta.servlet.http.HttpSession;
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
     * Ellenőrzi a Session állapotát, kinyeri a Client ID-t és lekéri a Player objektumot.
     *
     * @param session A bejövő HttpSession objektum.
     * @return Egy SessionContext objektumot, ami a validált adatokat tartalmazza.
     * @throws ResponseStatusException Hibás állapot esetén a kód automatikusan HTTP választ ad vissza.
     */
    public SessionContext validateAndGetContext(HttpSession session) {
        // --- 1. Client ID kinyerése és validálása ---
        Object clientIdObj = session.getAttribute("clientId");

        if (clientIdObj == null) {
            logger.warn("HIBÁS ÁLLAPOT: A 'clientId' attribútum hiányzik az HttpSession-ből.");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A munkamenet érvénytelen vagy nem inicializált.");
        }

        UUID currentClientId;
        try {
            currentClientId = UUID.fromString(clientIdObj.toString());
        } catch (IllegalArgumentException e) {
            logger.error("A sessionben tárolt 'clientId' formátuma érvénytelen: {}", clientIdObj);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FORBIDDEN");
        }

        // --- 2. Player lekérése a DB-ből ---
        Player player = playerRepository.findByClientId(currentClientId)
            .orElseThrow(() -> {
                logger.warn("Játékos nem található a DB-ben Client ID: {}", currentClientId);
                return new ResponseStatusException(HttpStatus.NOT_FOUND, "NOT_FOUND");
            });

        // --- 3. Redis ---
        Optional<GameState> gameStateOptional = gameStateService.getGameState(currentClientId);

        return new SessionContext(currentClientId, player, session, gameStateOptional);
    }
}
