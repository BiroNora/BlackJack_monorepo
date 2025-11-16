package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.GameState;
import org.springframework.stereotype.Service;

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
     * @param gameStateOptional Az aktuális állapot a Redisből.
     * @param clientId A játékos azonosítója.
     * @param betAmount Az elhelyezett tét.
     * @return A frissített GameState objektum.
     */
    // ...
    public GameState placeBet(Optional<GameState> gameStateOptional, UUID clientId, int betAmount) {
        GameState gameState;
        List<Integer> currentBetList;

        if (gameStateOptional.isEmpty()) {
            // 1. Új játék: Inicializálás
            gameState = GameState.builder()
                .clientId(clientId)
                .betList(new ArrayList<>())
                .phase("BET_PLACED")
                .build();
        } else {
            gameState = gameStateOptional.get();
        }

        currentBetList = gameState.getBetList();
        if (currentBetList == null) {
            currentBetList = new ArrayList<>();
        }

        // --- TÉT RÖGZÍTÉSE ---
        gameState.setBet(betAmount);

        // Hozzáadjuk a listához, hogy a Frontend tudja, hány tétet tett le
        currentBetList.add(betAmount);

        // Visszaírjuk a frissített listát
        gameState.setBetList(currentBetList);

        // Menti a frissített GameState-et a Redisbe
        gameStateService.saveGameState(gameState);
        return gameState;
    }
}
