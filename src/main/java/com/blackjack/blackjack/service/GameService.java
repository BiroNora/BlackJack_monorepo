package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class GameService {
    private static final Logger logger = LoggerFactory.getLogger(GameService.class);
    private final GameStateService gameStateService;
    private final GameRuleEngine gameRuleEngine;
    private final GameStateManager gameStateManager;
    private final DeckService deckService;

    public GameService(GameStateService gameStateService, GameRuleEngine gameRuleEngine, GameStateManager gameStateManager, DeckService deckService) {
        this.gameStateService = gameStateService;
        this.gameRuleEngine = gameRuleEngine;
        this.gameStateManager = gameStateManager;
        this.deckService = deckService;
    }

    public GameState placeBet(Optional<GameState> gameStateOptional, int betAmount) {
        GameState gameState = gameStateManager.getGameStateOrThrow(gameStateOptional);

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
        GameState gameState = gameStateManager.getGameStateOrThrow(gameStateOptional);

        List<Integer> betList = gameState.getBetList();

        if (betList == null || betList.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "BAD REQUEST"
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
        GameState gameState = gameStateManager.getGameStateOrThrow(gameStateOptional);
        Deck newDeck = deckService.createNewDeck();

        gameState.setDeck(newDeck);
        gameStateService.saveGameState(gameState);
        logger.info("+++++++++++ createNewDeck {}", gameState.getDeck());
        return gameState;
    }

    public Card safeDealCard(Optional<GameState> gameStateOptional) {
        GameState gameState = gameStateManager.getGameStateOrThrow(gameStateOptional);
        Deck deck = gameState.getDeck();

        if (deck == null || deck.getDeckLength() == 0) {
            logger.info("--- DECK STATUS: Deck was NULL or empty. Initializing new deck.");
            gameState = createNewDeck(gameStateOptional);
            deck = gameState.getDeck();
        }

        logger.info("++++++++++ safeDealCard deck size before deal: {}", gameState.getDeck().getDeckLength());

        try {
            Card card = deckService.dealCard(deck);
            gameStateService.saveGameState(gameState);
            logger.debug("--- DEAL SUCCESS: Dealt {}. Remaining size: {}", card, deck.getDeckLength());
            return card;

        } catch (IllegalStateException e) {
            logger.warn("!!! DECK EXHAUSTED: IllegalStateException caught. Starting automatic reshuffle/new deck.");

            GameState newGameState = createNewDeck(gameStateOptional);
            Deck newDeck = newGameState.getDeck();

            Card card = deckService.dealCard(newDeck);
            gameStateService.saveGameState(newGameState);
            logger.info("--- DEAL RESHUFFLE SUCCESS: Dealt {} from new deck. Remaining size: {}", card, newDeck.getDeckLength());
            return card;
        }
    }

    public GameState initializeNewRound(Optional<GameState> gameStateOptional) {
        GameState gameState = gameStateManager.getGameStateOrThrow(gameStateOptional);
        GameState resetGameState = gameStateManager.resetRoundState(gameState);
        resetGameState.setRoundActive(true);

        Card card1 = safeDealCard(gameStateOptional);
        Card card2 = safeDealCard(gameStateOptional);
        Card card3 = safeDealCard(gameStateOptional);
        Card card4 = safeDealCard(gameStateOptional);

        /*Card newAceCard1 = new Card(Suit.HEARTS, Rank.QUEEN);
        Card newAceCard3 = new Card(Suit.HEARTS, Rank.KING);
        Card newAceCard2 = new Card(Suit.DIAMONDS, Rank.QUEEN);
        Card newAceCard4 = new Card(Suit.CLUBS, Rank.ACE);

        Card card2 = newAceCard2;
        Card card4 = newAceCard4;
        Card card1 = newAceCard1;
        Card card3 = newAceCard3;*/

        String id = resetGameState.generateNextHandId();
        resetGameState.setAces(card1.rank() == Rank.ACE && card3.rank() == Rank.ACE);

        PlayerHand playerHand = resetGameState.getPlayer();
        playerHand.setId(id);
        playerHand.getHand().add(card1);
        playerHand.getHand().add(card3);
        LinkedList<Card> currentHand = playerHand.getHand();
        boolean isSplitPossible = gameRuleEngine.canSplit(currentHand);
        playerHand.setCanSplit(isSplitPossible);
        playerHand.setBet(resetGameState.getBet());

        DealerHandUnmasked dealerUnmasked = resetGameState.getDealerUnmasked();
        dealerUnmasked.getHand().add(card2);
        dealerUnmasked.getHand().add(card4);

        int initialOutcome = gameRuleEngine.initNatural21State(resetGameState);
        dealerUnmasked.setNatural21(initialOutcome);

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

        resetGameState.setNatural21(initialOutcome);
        gameStateService.saveGameState(resetGameState);
        logger.info("+++++++++++ deck {}", resetGameState.getDeck());
        return resetGameState;
    }

    public GameState playerHit(Optional<GameState> gameStateOptional) {
        GameState gameState = gameStateManager.getGameStateOrThrow(gameStateOptional);

        if (!gameState.isRoundActive()) {
            return gameState;
        }

        Card newCard = safeDealCard(gameStateOptional);
        gameState.getPlayer().getHand().add(newCard);
        gameStateService.saveGameState(gameState);
        logger.info("+++++++++++ playerHit {}", gameState.getDeck());
        return gameState;
    }

    public GameState playerStand(Optional<GameState> gameStateOptional) {
        GameState gameState = gameStateManager.getGameStateOrThrow(gameStateOptional);
        int dealerSum = gameState.getDealerUnmasked().getSum();
        int playerSum = gameState.getPlayer().getSum();

        if (playerSum <= 21) {
            while (dealerSum < 17) {
                Card newCard = safeDealCard(gameStateOptional);
                gameState.getDealerUnmasked().getHand().add(newCard);
                dealerSum = gameState.getDealerUnmasked().getSum();
            }
        }

        gameState.getPlayer().getHandState();
        gameState.getDealerUnmasked().getHandState();

        int calculatedWinnerValue = gameRuleEngine.winnerStateUpdater(gameState);
        gameState.setWinner(calculatedWinnerValue);
        gameStateService.saveGameState(gameState);

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
        } else if (globalWinnerState == WinnerState.PLAYER_WON.getValue() &&
            dealerUnmaskedNatural21 != WinnerState.BLACKJACK_DEALER_WON.getValue()) {
            rewardAmount = bet * 2.0;
        } else if ((globalWinnerState == WinnerState.PUSH.getValue() &&
            dealerUnmaskedNatural21 != WinnerState.BLACKJACK_DEALER_WON.getValue()) ||
            dealerUnmaskedNatural21 == WinnerState.BLACKJACK_PUSH.getValue()) {

            rewardAmount = bet * 1.0;
        }

        int finalReward = (int) Math.floor(rewardAmount);
        playerHand.setBet(0);
        gameState.setBet(0);
        gameState.setRoundActive(false);
        gameStateService.saveGameState(gameState);

        return new TransactionResult(gameState, finalReward);
    }

    public GameState doubleRequest(GameState gameState, int currentBet) {
        int doubledBet = currentBet * 2;
        gameState.getPlayer().setBet(doubledBet);
        gameStateService.saveGameState(gameState);

        return gameState;
    }

    public TransactionResult insuranceRequest(Optional<GameState> gameStateOptional) {
        GameState gameState = gameStateManager.getGameStateOrThrow(gameStateOptional);

        int currentBet = gameState.getBet();
        double halfBet = (double) currentBet / 2.0;
        double roundedCost = Math.ceil(halfBet);


        int natural21 = gameState.getDealerUnmasked().getNatural21();

        if (natural21 == 3) {
            gameState.setBet(0);
            gameState.getPlayer().setBet(0);
            gameState.setRoundActive(false);
            gameState.setWinner(WinnerState.DEALER_WON.getValue());
        }

        gameStateService.saveGameState(gameState);

        int insCost = natural21 == 3 ? currentBet : (int) roundedCost * -1;

        return new TransactionResult(gameState, insCost);
    }

    public GameState splitHand(Optional<GameState> gameStateOptional) {
        GameState gameState = gameStateManager.getGameStateOrThrow(gameStateOptional);
        PlayerHand playerHand = gameState.getPlayer();

        if (!playerHand.isCanSplit() || gameState.getPlayers() == null || gameState.getPlayers().size() > 3) {
            return gameState;
        }

        Card cardToSplit = playerHand.getHand().remove(1);
        PlayerHand updatedHand = gameStateManager.dealSplitCard(gameState, true, null);
        gameState.setPlayer(updatedHand);
        PlayerHand secondPlayer = gameStateManager.dealSplitCard(gameState, false, cardToSplit);
        gameState.getPlayers().put(secondPlayer.getId(), secondPlayer);
        gameStateManager.sortPlayersMap(gameState.getPlayers());

        gameState.addSplitReq(1);
        gameStateService.saveGameState(gameState);
        //logger.info("gameState: {}", gameState);
        return gameState;
    }

    public GameState addToPlayersListByStand(Optional<GameState> gameStateOptional) {
        GameState gameState = gameStateManager.getGameStateOrThrow(gameStateOptional);
        PlayerHand playerHand = gameState.getPlayer();
        boolean isActive = gameStateManager.areStatedFalseHands(gameState.getPlayers());
        logger.info(">>>>y isActive: {}", isActive);
        if (isActive) {
            playerHand.setStated(true);
            gameState.getPlayers().put(playerHand.getId(), playerHand);
            String ID = playerHand.getId();
            gameState.getPlayersIndex().put(ID, true);
        }

        gameStateService.saveGameState(gameState);
        //logger.info("gameState: {}", gameState);
        return gameState;
    }

    public GameState addSplitPlayerToGame(Optional<GameState> gameStateOptional) {
        GameState gameState = gameStateManager.getGameStateOrThrow(gameStateOptional);
        Map<String, Boolean> playersIndex = gameState.getPlayersIndex();

        Optional<String> nextIdOptional = gameStateManager.findNextActivePlayerId(playersIndex);
        PlayerHand player = gameState.getPlayer();
        PlayerHand splitPlayer = gameState.getSplitPlayer();

        if (nextIdOptional.isEmpty()) {
            return gameState;
        }

        String nextHandId = nextIdOptional.get();
        // ESET 1: VISSZATÖLTÉS (Cache Védelme a Strict Mode miatt)
        if (splitPlayer != null && splitPlayer.getId() != null && splitPlayer.getId().equals(nextHandId)) {
            gameState.setPlayer(splitPlayer);
        }
        // ESET 2: Gyors kiút (A lap már be van töltve)
        else if (player != null && player.getId().equals(nextHandId)) {
        }
        // ESET 3: ELSŐ FUTÁS (Lap kiemelése, mentés, és splitReq csökkentése)
        else if (gameState.getPlayers().containsKey(nextHandId)) {
            Map<String, PlayerHand> playersMap = gameState.getPlayers();

            PlayerHand nextHand = playersMap.remove(nextHandId);
            gameState.setPlayer(nextHand);
            gameState.setSplitPlayer(gameStateManager.deepCopyPlayerHand(nextHand));
            gameState.addSplitReq(-1);
        }

        PlayerHand newPlayer = gameState.getPlayer();

        if (newPlayer.getHand().size() < 2) {
            Card cardToAdd = safeDealCard(gameStateOptional);
            newPlayer.getHand().add(cardToAdd);
            boolean canSplit = gameRuleEngine.canSplit(newPlayer.getHand());
            newPlayer.setCanSplit(canSplit);
        }

        gameStateService.saveGameState(gameState);
        //logger.info("gameState: {}", gameState);
        return gameState;
    }

    public GameState addPlayerFromPlayers(Optional<GameState> gameStateOptional) {
        GameState gameState = gameStateManager.getGameStateOrThrow(gameStateOptional);
        Map<String, PlayerHand> playersMap = gameState.getPlayers();
        PlayerHand extractedHand;

        if (playersMap == null || playersMap.isEmpty()) {
            return gameState;
        }

        Map.Entry<String, PlayerHand> firstEntry = playersMap.entrySet().iterator().next();
        String firstId = firstEntry.getKey();
        extractedHand = playersMap.remove(firstId);
        gameState.setPlayer(extractedHand);

        gameStateService.saveGameState(gameState);

        return gameState;
    }

    public GameState restartGame(Optional<GameState> gameStateOptional) {
        GameState gameState = gameStateManager.getGameStateOrThrow(gameStateOptional);
        GameState resetGameState = gameStateManager.resetRoundState(gameState);
        resetGameState.setBet(0);

        gameStateService.saveGameState(resetGameState);

        return resetGameState;
    }

    public record TransactionResult(GameState gameState, int transactionResult) {
        // A record automatikusan biztosítja a konstruktort és a gettereket.
    }
}
