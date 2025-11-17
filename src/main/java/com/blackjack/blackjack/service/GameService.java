package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.GameState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GameService {

    private static final Logger logger = LoggerFactory.getLogger(GameService.class);
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
        int currentTotalBet = gameState.getBet();
        int newTotalBet = currentTotalBet + betAmount; // currentTotalBet += betAmount

        gameState.setBet(newTotalBet);
        currentBetList.add(betAmount);
        gameState.setBetList(currentBetList);

        // Menti a frissített GameState-et a Redisbe
        gameStateService.saveGameState(gameState);

        return gameState;
    }

    /**
     * Eltávolítja az utoljára elhelyezett tétet a GameState-ből, frissíti az összeget, és elmenti.
     *
     * @param gameStateOptional Az aktuális állapot a Redisből.
     * @return A visszavett tét összege.
     */
    public int retakeBet(Optional<GameState> gameStateOptional) {

        GameState gameState = gameStateOptional.orElseThrow(() -> {
            return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "A játékállapot (GameState) nem található. Kérem, indítsa újra a session-t az /api/initialize_session végponton!"
            );
        });

        List<Integer> betList = gameState.getBetList();

        // 1. Ellenőrzés: Van-e tét, amit vissza lehet venni?
        if (betList == null || betList.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Nincs elhelyezett tét, amit vissza lehetne venni."
            );
        }

        // 3. Logika: Utolsó tét eltávolítása
        int amountToReturn = betList.remove(betList.size() - 1);

        int currentTotalBet = gameState.getBet();
        int newTotalBet = currentTotalBet - amountToReturn;

        gameState.setBet(newTotalBet);

        logger.info("Játékállapot frissítve (TÉT VISSZAVÉTEL): Utolsó tét: {}, Új teljes tét (bet): {}",
            amountToReturn, gameState.getBet());

        // 5. Fázis visszaállítása, ha elfogytak a tétek
        if (betList.isEmpty()) {
            gameState.setPhase("AWAITING_BET");
            gameState.setRoundActive(false);
        }

        gameState.setBetList(betList);

        // 6. Mentés és a visszavett összeg visszaadása
        gameStateService.saveGameState(gameState);
        return amountToReturn * -1;
    }
}
