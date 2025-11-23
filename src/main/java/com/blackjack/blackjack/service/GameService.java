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
        GameState gameState = getGameStateOrThrow(gameStateOptional);

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
        GameState gameState = getGameStateOrThrow(gameStateOptional);

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
            gameState.setRoundActive(false);
        }

        gameState.setBetList(betList);
        gameStateService.saveGameState(gameState);

        return amountToReturn * -1;
    }

    public GameState createNewDeck(Optional<GameState> gameStateOptional) {
        GameState gameState = getGameStateOrThrow(gameStateOptional);
        Deck newDeck = new Deck();

        gameState.setDeck(newDeck);
        gameStateService.saveGameState(gameState);

        return gameState;
    }

    public GameState initializeNewRound(Optional<GameState> gameStateOptional) {
        GameState gameState = getGameStateOrThrow(gameStateOptional);
        GameState resetGameState = resetRoundState(gameState);
        resetGameState.setRoundActive(true);

        Deck deck = gameState.getDeck();
        Card card1 = deck.dealCard();
        Card card2 = deck.dealCard();
        Card card3 = deck.dealCard();
        Card card4 = deck.dealCard();

        String id = resetGameState.generateNextHandId();
        int initialOutcome = initNatural21State(resetGameState);
        resetGameState.setAces(card1.rank() == Rank.ACE && card3.rank() == Rank.ACE);

        PlayerHand playerHand = resetGameState.getPlayer();
        playerHand.setId(id);
        playerHand.getHand().add(card1);
        playerHand.getHand().add(card3);
        List<Card> currentHand = playerHand.getHand();
        boolean isSplitPossible = canSplit(currentHand);
        playerHand.setCanSplit(isSplitPossible);
        playerHand.setBet(resetGameState.getBet());

        DealerHandMasked dealerMasked = resetGameState.getDealerMasked();
        dealerMasked.getHand().add(Card.createMaskedCard());
        dealerMasked.getHand().add(card4);

        List<Card> maskedHand = List.of(card4);
        int dealerSum = HandValueCalculator.calculateSum(maskedHand);
        dealerMasked.setSum(dealerSum);

        boolean canInsure = card4.rank() == Rank.ACE;
        dealerMasked.setCanInsure(canInsure);

        int maskedState = (initialOutcome == WinnerState.BLACKJACK_DEALER_WON.getValue())
            ? WinnerState.NONE.getValue()
            : initialOutcome;
        dealerMasked.setNat21(maskedState);

        DealerHandUnmasked dealerUnmasked = resetGameState.getDealerUnmasked();
        dealerUnmasked.getHand().add(card2);
        dealerUnmasked.getHand().add(card4);
        dealerUnmasked.setNatural21(initialOutcome);

        return resetGameState;
    }

    public GameState playerHit(Optional<GameState> gameStateOptional) {
        GameState gameState = getGameStateOrThrow(gameStateOptional);

        if (!gameState.isRoundActive()) {
            return gameState;
        }

        Card newCard = gameState.getDeck().dealCard();
        gameState.getPlayer().getHand().add(newCard);

        return gameState;
    }

    public GameState playerStand(Optional<GameState> gameStateOptional) {
        GameState gameState = getGameStateOrThrow(gameStateOptional);
        int dealerSum = gameState.getDealerUnmasked().getSum();
        int playerSum = gameState.getPlayer().getSum();

        if (playerSum <= 21) {
            while (dealerSum < 17) {
                Card newCard = gameState.getDeck().dealCard();
                gameState.getDealerUnmasked().getHand().add(newCard);
                dealerSum = gameState.getDealerUnmasked().getSum();
            }
        }

        gameState.getPlayer().getHandState();
        gameState.getDealerUnmasked().getHandState();

        int calculatedWinnerValue = winnerStateUpdater(gameState);
        gameState.setWinner(calculatedWinnerValue);

        return gameState;
    }

    public TransactionResult calculateRewards(GameState gameState) {
        PlayerHand playerHand = gameState.getPlayer();
        DealerHandUnmasked dealerUnmasked = gameState.getDealerUnmasked();

        int bet = playerHand.getBet();

        int globalWinnerState = gameState.getWinner();
        int initialNatural21 = gameState.getNatural21();
        int dealerUnmaskedNatural21 = dealerUnmasked.getNatural21();

        double rewardAmount = 0.0;

        if (initialNatural21 == WinnerState.BLACKJACK_PLAYER_WON.getValue()) {
            rewardAmount = bet * 2.5;
        }

        else if (globalWinnerState == WinnerState.PLAYER_WON.getValue() &&
            dealerUnmaskedNatural21 != WinnerState.BLACKJACK_DEALER_WON.getValue()) {
            rewardAmount = bet * 2.0;
        }

        else if ((globalWinnerState == WinnerState.PUSH.getValue() &&
            dealerUnmaskedNatural21 != WinnerState.BLACKJACK_DEALER_WON.getValue()) ||
            dealerUnmaskedNatural21 == WinnerState.BLACKJACK_PUSH.getValue()) {

            rewardAmount = bet * 1.0;
        }

        playerHand.setBet(0);
        gameState.setBet(0);

        gameState.setRoundActive(false);

        int finalReward = (int) Math.floor(rewardAmount);

        return new TransactionResult(gameState, finalReward);
    }


    // helpers
    private GameState getGameStateOrThrow(Optional<GameState> gameStateOptional) {
        return gameStateOptional.orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "CLIENT_ERROR_SPECIFIC"
        ));
    }

    public GameState resetRoundState(GameState gameState) {
        gameState.setPlayer(PlayerHand.createEmptyHand());
        gameState.setDealerMasked(DealerHandMasked.createEmptyHand());
        gameState.setDealerUnmasked(DealerHandUnmasked.createEmptyHand());
        gameState.setAces(false);
        gameState.setNatural21(0);
        gameState.setWinner(0);
        gameState.setHandCounter(0);
        gameState.setSplitReq(0);
        gameState.setBetList(List.of());
        gameState.setRoundActive(false);

        return gameState;
    }

    public int initNatural21State(GameState gameState) {
        PlayerHand playerHand = gameState.getPlayer();
        DealerHandUnmasked dealerHand = gameState.getDealerUnmasked();

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

        return naturalState.getValue();
    }

    public int winnerStateUpdater(GameState gameState) {
        PlayerHand playerHand = gameState.getPlayer();
        DealerHandUnmasked dealerHand = gameState.getDealerUnmasked();

        int playerSum = playerHand.getSum();
        int dealerSum = dealerHand.getSum();
        WinnerState winnerState;

        if (playerSum > 21) {
            winnerState = WinnerState.PLAYER_LOST;
        }
        else if (dealerSum > 21) {
            winnerState = WinnerState.PLAYER_WON;
        }
        else if (playerSum == dealerSum) {
            winnerState = WinnerState.PUSH;
        }
        else if (playerSum > dealerSum) {
            winnerState = WinnerState.PLAYER_WON;
        }
        else {
            winnerState = WinnerState.DEALER_WON;
        }

        return winnerState.getValue();
    }

    public record TransactionResult(GameState gameState, int transactionResult) {
        // A record automatikusan biztosítja a konstruktort és a gettereket.
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
