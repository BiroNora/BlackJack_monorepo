package com.blackjack.blackjack.service;

import com.blackjack.blackjack.exception.GameRuleException;
import com.blackjack.blackjack.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class GameService {

    private static final Logger logger = LoggerFactory.getLogger(GameService.class);
    private final GameStateService gameStateService;

    public GameService(GameStateService gameStateService) {
        this.gameStateService = gameStateService;
    }

    public GameState placeBet(Optional<GameState> gameStateOptional, int betAmount) {

        GameState gameState = gameStateOptional.orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "A játékállapot nem található. Kérem, indítsa újra a session-t az /api/initialize_session végponton!"
        ));

        List<Integer> currentBetList = gameState.getBetList();

        if (currentBetList == null) {
            currentBetList = new ArrayList<>();
        }

        int currentTotalBet = gameState.getBet();
        currentTotalBet += betAmount;

        gameState.setBet(currentTotalBet);
        currentBetList.add(betAmount);
        gameState.setBetList(currentBetList);

        gameStateService.saveGameState(gameState);

        return gameState;
    }

    public int retakeBet(Optional<GameState> gameStateOptional) {

        GameState gameState = gameStateOptional.orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "A játékállapot (GameState) nem található. Kérem, indítsa újra a session-t az /api/initialize_session végponton!"
        ));

        List<Integer> betList = gameState.getBetList();

        if (betList == null || betList.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Nincs elhelyezett tét, amit vissza lehetne venni."
            );
        }

        int amountToReturn = betList.remove(betList.size() - 1);

        int currentTotalBet = gameState.getBet();
        int newTotalBet = currentTotalBet - amountToReturn;

        gameState.setBet(newTotalBet);

        if (betList.isEmpty()) {
            gameState.setPhase("AWAITING_BET");
            gameState.setRoundActive(false);
        }

        gameState.setBetList(betList);
        gameStateService.saveGameState(gameState);

        return amountToReturn * -1;
    }

    public GameState createNewDeck(Optional<GameState> gameStateOptional) {

        GameState gameState = gameStateOptional.orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "A játékállapot (GameState) nem található. Kérem, indítsa újra a session-t az /api/initialize_session végponton!"
        ));

        Deck newDeck = new Deck();

        gameState.setDeck(newDeck);
        gameStateService.saveGameState(gameState);

        return gameState;
    }

    /**
     * Visszaállítja a GameState-et a következő kör megkezdéséhez szükséges állapotra.
     * MEGSZÜNTETI a játékos és osztó lapokat, téteket és körállapotokat.
     */
    public GameState resetRoundState(GameState gameState) {
        gameState.setPlayerHand(PlayerHand.createEmptyHand());
        gameState.setSplitPlayerHand(null);
        gameState.setDealerHandMasked(DealerHandMasked.createEmptyHand());
        gameState.setDealerHandUnMasked(DealerHandUnmasked.createEmptyHand());
        gameState.setAces(false);
        gameState.setNatural21(WinnerState.NONE);
        gameState.setWinner(WinnerState.NONE);
        gameState.setHandCounter(0);
        gameState.setSplitReq(0);
        gameState.setUnmaskedSumSent(false);
        gameState.setBetList(List.of());
        gameState.setBet(0);
        gameState.setRoundActive(false);

        return gameState;
    }


    public void createSplitHand(PlayerHand originalHand) {
        List<Card> cards = originalHand.getHand();

        // 1. Lapok számának ellenőrzése
        if (cards.size() != 2) {
            throw new GameRuleException("A hasításhoz pontosan két lap szükséges.");
        }

        // 2. Rang ellenőrzése (ez a kulcs!)
        // Összehasonlítja az első lap rangját (rank) a második lap rangjával.
        if (cards.get(0).rank() != cards.get(1).rank()) {
            throw new GameRuleException("A lapoknak azonos rangúnak kell lenniük a hasításhoz.");
        }

        // ... ha az ellenőrzések sikeresek, akkor folytatódik a hasítási logika,
        // és meghívható a PlayerHand.createSplitHand(originalHand)
    }
}
