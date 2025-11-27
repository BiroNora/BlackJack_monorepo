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
    private static final Comparator<PlayerHand> SPLIT_HAND_COMPARATOR = Comparator
        .comparing(PlayerHand::isStated)
        .thenComparing(PlayerHand::getId);

    public void sortPlayersByStateAndId(GameState gameState) {
        Map<String, PlayerHand> playersMap = gameState.getPlayers();

        if (playersMap == null || playersMap.isEmpty()) {
            return;
        }

        List<PlayerHand> sortedHands = new ArrayList<>(playersMap.values());
        sortedHands.sort(SPLIT_HAND_COMPARATOR);
        playersMap.clear();

        for (PlayerHand hand : sortedHands) {
            playersMap.put(hand.getId(), hand);
        }
    }

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
        //Card card1 = deck.dealCard();
        //Card card2 = deck.dealCard();
        //Card card3 = deck.dealCard();
        //Card card4 = deck.dealCard();

        Card newAceCard1 = new Card(Suit.HEARTS, Rank.QUEEN);
        Card newAceCard3 = new Card(Suit.HEARTS, Rank.KING);
        Card newAceCard2 = new Card(Suit.DIAMONDS, Rank.QUEEN);
        Card newAceCard4 = new Card(Suit.CLUBS, Rank.ACE);
        // Vagy használja a Card osztályban lévő segédmetódust, ha van:
        // Card newAceCard = Card.createAce(Suit.HEARTS);

        Card card2 = newAceCard2;
        Card card4 = newAceCard4;
        Card card1 = newAceCard1;
        Card card3 = newAceCard3;

        String id = resetGameState.generateNextHandId();
        resetGameState.setAces(card1.rank() == Rank.ACE && card3.rank() == Rank.ACE);

        PlayerHand playerHand = resetGameState.getPlayer();
        playerHand.setId(id);
        playerHand.getHand().add(card1);
        playerHand.getHand().add(card3);
        LinkedList<Card> currentHand = playerHand.getHand();
        boolean isSplitPossible = canSplit(currentHand);
        playerHand.setCanSplit(isSplitPossible);
        playerHand.setBet(resetGameState.getBet());

        DealerHandUnmasked dealerUnmasked = resetGameState.getDealerUnmasked();
        dealerUnmasked.getHand().add(card2);
        dealerUnmasked.getHand().add(card4);

        int initialOutcome = initNatural21State(resetGameState);
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

        return resetGameState;
    }

    public GameState playerHit(Optional<GameState> gameStateOptional) {
        GameState gameState = getGameStateOrThrow(gameStateOptional);

        if (!gameState.isRoundActive()) {
            return gameState;
        }

        Card newCard = gameState.getDeck().dealCard();
        gameState.getPlayer().getHand().add(newCard);
        gameStateService.saveGameState(gameState);

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
        GameState gameState = getGameStateOrThrow(gameStateOptional);
        logger.info("--- gamestate --- {}", gameState);

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
        logger.info("--- insCost --- {}", insCost);
        logger.info("--- gamestate --- {}", gameState);
        return new TransactionResult(gameState, insCost);
    }

    public GameState splitHand(Optional<GameState> gameStateOptional) {
        GameState gameState = getGameStateOrThrow(gameStateOptional);
        PlayerHand playerHand = gameState.getPlayer();

        if (!playerHand.isCanSplit() || gameState.getPlayers() == null || gameState.getPlayers().size() > 3) {
            return gameState;
        }

        Card cardToSplit = playerHand.getHand().remove(1);
        PlayerHand updatedHand = dealSplitCard(gameState, true, null);
        gameState.setPlayer(updatedHand);
        PlayerHand secondPlayer = dealSplitCard(gameState, false, cardToSplit);
        gameState.getPlayers().put(secondPlayer.getId(), secondPlayer);
        sortPlayersMap(gameState.getPlayers());

        gameState.addSplitReq(1);
        gameStateService.saveGameState(gameState);
        //logger.info("gameState: {}", gameState);
        return gameState;
    }

    public GameState addToPlayersListByStand(Optional<GameState> gameStateOptional) {
        GameState gameState = getGameStateOrThrow(gameStateOptional);
        PlayerHand playerHand = gameState.getPlayer();
        boolean isActive = areStatedFalseHands(gameState.getPlayers());
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
        GameState gameState = getGameStateOrThrow(gameStateOptional);
        Map<String, Boolean> playersIndex = gameState.getPlayersIndex();

        Optional<String> nextIdOptional = findNextActivePlayerId(playersIndex);
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
            gameState.setSplitPlayer(deepCopyPlayerHand(nextHand));
            gameState.addSplitReq(-1);
        }

        PlayerHand newPlayer = gameState.getPlayer();

        if (newPlayer.getHand().size() < 2) {
            Deck deck = gameState.getDeck();
            Card cardToAdd = deck.dealCard();
            newPlayer.getHand().add(cardToAdd);
            boolean canSplit = canSplit(newPlayer.getHand());
            newPlayer.setCanSplit(canSplit);
        }

        gameStateService.saveGameState(gameState);
        //logger.info("gameState: {}", gameState);
        return gameState;
    }

    public GameState addPlayerFromPlayers(Optional<GameState> gameStateOptional) {
        GameState gameState = getGameStateOrThrow(gameStateOptional);
        Map<String, PlayerHand> playersMap = gameState.getPlayers();

        if (playersMap == null) {
            return gameState;
        }
        PlayerHand firstHand = playersMap.remove(0);
        gameState.setPlayer(firstHand);

        gameStateService.saveGameState(gameState);
        //logger.info("gameState: {}", gameState);
        return gameState;
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
        gameState.setSplitPlayer(PlayerHand.createEmptyHand());
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
        } else if (dealerSum > 21) {
            winnerState = WinnerState.PLAYER_WON;
        } else if (playerSum == dealerSum) {
            winnerState = WinnerState.PUSH;
        } else if (playerSum > dealerSum) {
            winnerState = WinnerState.PLAYER_WON;
        } else {
            winnerState = WinnerState.DEALER_WON;
        }

        return winnerState.getValue();
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

    public PlayerHand dealSplitCard(GameState gameState, boolean isFirst, Card cardToSplit) {
        PlayerHand playerHand;
        Card card1;

        if (isFirst) {
            playerHand = gameState.getPlayer();
            Deck deck = gameState.getDeck();
            card1 = deck.dealCard();
            playerHand.getHand().add(card1);
            LinkedList<Card> currentHand = playerHand.getHand();
            boolean isSplitPossible = canSplit(currentHand);
            if (currentHand.getFirst().rank() == Rank.ACE && currentHand.getLast().rank() == Rank.ACE) {
                playerHand.setCanSplit(false);
            } else {
                playerHand.setCanSplit(isSplitPossible);
            }
            playerHand.setBet(gameState.getBet());
            playerHand.setStated(false);
        } else {
            playerHand = PlayerHand.createEmptyHand();
            String id = gameState.generateNextHandId();
            playerHand.setId(id);
            playerHand.getHand().add(cardToSplit);
            playerHand.setBet(gameState.getBet());
        }

        gameState.getPlayersIndex().put(playerHand.getId(), false);

        return playerHand;
    }

    public boolean areStatedFalseHands(Map<String, PlayerHand> playersMap) {
        if (playersMap == null || playersMap.isEmpty()) {
            return false;
        }
        return playersMap.values().stream()
            .anyMatch(hand -> !hand.isStated());
    }

    public Optional<String> findNextActivePlayerId(Map<String, Boolean> playersIndex) {
        if (playersIndex == null || playersIndex.isEmpty()) {
            return Optional.empty();
        }

        return playersIndex.entrySet().stream()
            .filter(entry -> !entry.getValue())
            .map(Map.Entry::getKey)
            .sorted()
            .findFirst();
    }

    private PlayerHand deepCopyPlayerHand(PlayerHand original) {
        if (original == null) {
            return null;
        }

        LinkedList<Card> copiedHand = new LinkedList<>(original.getHand());

        return PlayerHand.builder()
            .id(original.getId())
            .hand(copiedHand)
            .canSplit(original.isCanSplit())
            .stated(original.isStated())
            .bet(original.getBet())
            .build();
    }

    public void sortPlayersMap(Map<String, PlayerHand> playersMap) {
        if (playersMap == null || playersMap.isEmpty()) {
            return;
        }

        List<PlayerHand> sortedHands = new ArrayList<>(playersMap.values());
        sortedHands.sort(SPLIT_HAND_COMPARATOR);
        playersMap.clear();

        for (PlayerHand hand : sortedHands) {
            playersMap.put(hand.getId(), hand);
        }
    }

    public record TransactionResult(GameState gameState, int transactionResult) {
        // A record automatikusan biztosítja a konstruktort és a gettereket.
    }
}
