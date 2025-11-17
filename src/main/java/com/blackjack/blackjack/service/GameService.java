package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.GameState;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GameService {

    private final GameStateService gameStateService;

    public GameService(GameStateService gameStateService) {
        this.gameStateService = gameStateService;
    }

    /**
     * Létrehozza vagy frissíti a GameState-et a tét elhelyezése után.
     *
     * @param gameStateOptional Az aktuális állapot a Redisből.
     * @param clientId          A játékos azonosítója.
     * @param betAmount         Az elhelyezett tét.
     * @return A frissített GameState objektum.
     */
    // ...
    public GameState placeBet(Optional<GameState> gameStateOptional, UUID clientId, int betAmount) {

        GameState gameState = gameStateOptional.orElseThrow(() -> {
            return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "A játékállapot nem található. Kérem, indítsa újra a session-t az /api/initialize_session végponton!"
            );
        });

        List<Integer> currentBetList = gameState.getBetList();

        // Védelmi réteg a null listák ellen (bár az init-nek be kell állítania)
        if (currentBetList == null) {
            currentBetList = new ArrayList<>();
        }

        // --- TÉT RÖGZÍTÉSE ---
        gameState.setBet(betAmount);
        currentBetList.add(betAmount);
        gameState.setBetList(currentBetList);

        // Menti a frissített GameState-et a Redisbe
        gameStateService.saveGameState(gameState);

        return gameState;
    }
}
