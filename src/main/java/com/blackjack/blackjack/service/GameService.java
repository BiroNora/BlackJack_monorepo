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

    public GameState initializeNewRound(Optional<GameState> gameStateOptional) {
        GameState gameState = gameStateOptional.orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "A játékállapot (GameState) nem található. Kérem, indítsa újra a session-t az /api/initialize_session végponton!"
        ));
        GameState resetGameState = resetRoundState(gameState);
        resetGameState.setRoundActive(true);

        Deck deck = gameState.getDeck();
        Card card1 = deck.dealCard();
        Card card2 = deck.dealCard();
        Card card3 = deck.dealCard();
        Card card4 = deck.dealCard();

        String id = resetGameState.generateNextHandId();
        WinnerState result = initNatural21State(resetGameState);
        resetGameState.setAces(card1.rank() == Rank.ACE && card3.rank() == Rank.ACE);

        PlayerHand playerHand = resetGameState.getPlayerHand();
        playerHand.setId(id);
        playerHand.getHand().add(card1);
        playerHand.getHand().add(card3);
        List<Card> currentHand = playerHand.getHand();
        boolean isSplitPossible = canSplit(currentHand);
        playerHand.setCanSplit(isSplitPossible);
        playerHand.setBet(resetGameState.getBet());

        DealerHandMasked dealerMasked = resetGameState.getDealerHandMasked();
        dealerMasked.getHand().add(Card.createMaskedCard());
        dealerMasked.getHand().add(card4);

        List<Card> maskedHand = List.of(card4);
        int dealerSum = HandValueCalculator.calculateSum(maskedHand);
        dealerMasked.setSum(dealerSum);

        boolean canInsure = card4.rank() == Rank.ACE;
        dealerMasked.setCanInsure(canInsure);

        WinnerState maskedState;
        if (result == WinnerState.BLACKJACK_DEALER_WON) {
            maskedState = WinnerState.NONE;
        } else {
            maskedState = result;
        }
        dealerMasked.setNatural21State(maskedState);

        DealerHandUnmasked dealerUnmasked = resetGameState.getDealerHandUnMasked();
        dealerUnmasked.getHand().add(card2);
        dealerUnmasked.getHand().add(card4);
        dealerMasked.setNatural21State(result);

        return resetGameState;
    }

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
        gameState.setRoundActive(false);

        return gameState;
    }

    public WinnerState initNatural21State(GameState gameState) {
        PlayerHand playerHand = gameState.getPlayerHand();
        DealerHandUnmasked dealerHand = gameState.getDealerHandUnMasked();

        int playerSum = playerHand.getSum();
        int dealerSum = dealerHand.getSum();

        boolean playerNatural = (playerHand.getHand().size() == 2) && (playerSum == 21);
        boolean dealerNatural = (dealerHand.getHand().size() == 2) && (dealerSum == 21);

        WinnerState naturalState;

        if (playerNatural && dealerNatural) {
            naturalState = WinnerState.BLACKJACK_PUSH;
        } else if (playerNatural) {
            naturalState = WinnerState.BLACKJACK_PLAYER_WON;
        } else if (dealerNatural) {
            naturalState = WinnerState.BLACKJACK_DEALER_WON;
        } else {
            naturalState = WinnerState.NONE;
        }

        return naturalState;
    }

    public boolean canSplit(List<Card> hand) {
        if (hand == null || hand.size() != 2) {
            return false;
        }

        Rank rank1 = hand.get(0).rank();
        Rank rank2 = hand.get(1).rank();

        boolean sameRank = rank1 == rank2;
        boolean bothAreTenValue = rank1.getBaseValue() == 10 && rank2.getBaseValue() == 10;

        return sameRank || bothAreTenValue;
    }

    public void createSplitHand(PlayerHand originalHand) {
        List<Card> cards = originalHand.getHand();

        if (cards.size() != 2) {
            throw new GameRuleException("A hasításhoz pontosan két lap szükséges.");
        }

        if (cards.get(0).rank() != cards.get(1).rank()) {
            throw new GameRuleException("A lapoknak azonos rangúnak kell lenniük a hasításhoz.");
        }

        // ... ha az ellenőrzések sikeresek, akkor folytatódik a hasítási logika,
        // és meghívható a PlayerHand.createSplitHand(originalHand)
    }
}
