package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GameService {
    private static final Logger logger = LoggerFactory.getLogger(GameService.class);
    private final GameRuleEngine gameRuleEngine;
    private final GameStateManager gameStateManager;
    private final HandValueCalculator handValueCalculator;
    private final HandStateUpdater handStateUpdater;

    public GameService(GameRuleEngine gameRuleEngine, GameStateManager gameStateManager, HandValueCalculator handValueCalculator, HandStateUpdater handStateUpdater) {
        this.gameRuleEngine = gameRuleEngine;
        this.gameStateManager = gameStateManager;
        this.handValueCalculator = handValueCalculator;
        this.handStateUpdater = handStateUpdater;
    }

    // --- STANDARD ---
    // VALÓS ÁG
    public GameState initializeNewRound2(GameState state) {
        // 1. Kör állapotának nullázása (resetRoundState immutábilis)
        GameState currentGameState = gameStateManager.resetRoundState(state)
            .toBuilder()
            .isRoundActive(true)
            .build();

        // ----------------------------------------------------
        // 2. KÁRTYA OSZTÁSI LÁNC (Tisztán objektumokkal)
        // ----------------------------------------------------
        DealResultWithState deal1 = gameStateManager.safeDealCard(currentGameState);
        Card card1 = deal1.dealtCard();
        currentGameState = deal1.newGameState();

        DealResultWithState deal2 = gameStateManager.safeDealCard(currentGameState);
        Card card2 = deal2.dealtCard();
        currentGameState = deal2.newGameState();

        DealResultWithState deal3 = gameStateManager.safeDealCard(currentGameState);
        Card card3 = deal3.dealtCard();
        currentGameState = deal3.newGameState();

        DealResultWithState deal4 = gameStateManager.safeDealCard(currentGameState);
        Card card4 = deal4.dealtCard();
        currentGameState = deal4.newGameState();

        // ----------------------------------------------------
        // 3. ADATOK ELŐKÉSZÍTÉSE ÉS SZÁMÍTÁSOK
        // ----------------------------------------------------
        LinkedList<Card> playerStartHand = new LinkedList<>(List.of(card1, card3));
        int playerSum = handValueCalculator.calculateSum(playerStartHand);
        HandState playerHandState = handStateUpdater.updateHandState(playerSum, playerStartHand.size(), currentGameState.isWasSplitInRound());
        boolean isSplitPossible = gameRuleEngine.canSplit(playerStartHand);

        int nextCount = currentGameState.calculateNextHandCounter();
        String handId = String.format("P-%03d", nextCount);

        LinkedList<Card> dealerUnmaskedHand = new LinkedList<>(List.of(card2, card4));
        int dealerFullSum = handValueCalculator.calculateSum(dealerUnmaskedHand);
        HandState dealerHandState = handStateUpdater.updateHandState(dealerFullSum, dealerUnmaskedHand.size(), currentGameState.isWasSplitInRound());

        // ----------------------------------------------------
        // 4. IDEIGLENES OBJEKTUMOK ÉS BLACKJACK ELLENŐRZÉS
        // ----------------------------------------------------
        assert currentGameState.getPlayer() != null;
        PlayerHand tempPlayer = currentGameState.getPlayer().toBuilder()
            .id(handId)
            .hand(playerStartHand)
            .sum(playerSum)
            .handState(playerHandState.getValue())
            .canSplit(isSplitPossible)
            .bet(currentGameState.getBet())
            .build();

        assert currentGameState.getDealerUnmasked() != null;
        DealerHandUnmasked tempDealerUnmasked = currentGameState.getDealerUnmasked().toBuilder()
            .hand(dealerUnmaskedHand)
            .sum(dealerFullSum)
            .build();

        // Átmeneti állapot a RuleEngine számára (hogy lássa mindkét kezet)
        GameState stateForCalculation = currentGameState.toBuilder()
            .player(tempPlayer)
            .dealerUnmasked(tempDealerUnmasked)
            .build();

        int initialOutcome = gameRuleEngine.initNatural21State(stateForCalculation);

        // ----------------------------------------------------
        // 5. VÉGLEGES OBJEKTUMOK ÖSSZESZERELÉSE
        // ----------------------------------------------------
        DealerHandUnmasked finalDealerUnmasked = tempDealerUnmasked.toBuilder()
            .handState(dealerHandState.getValue())
            .natural21(initialOutcome)
            .build();

        boolean canInsure = card4.rank() == Rank.ACE;
        int maskedStateForDealer = (initialOutcome == WinnerState.BLACKJACK_DEALER_WON.getValue())
            ? WinnerState.NONE.getValue()
            : initialOutcome;

        assert currentGameState.getDealerMasked() != null;
        DealerHandMasked updatedDealerMasked = currentGameState.getDealerMasked().toBuilder()
            .hand(new LinkedList<>(List.of(Card.createMaskedCard(), card4)))
            .canInsure(canInsure)
            .nat21(maskedStateForDealer)
            .build();

        // Visszatérünk a kész, új állapottal (MENTÉS NÉLKÜL)
        return currentGameState.toBuilder()
            .player(tempPlayer)
            .dealerUnmasked(finalDealerUnmasked)
            .dealerMasked(updatedDealerMasked)
            .handCounter(nextCount)
            .natural21(initialOutcome)
            .isRoundActive(true)
            .aces(card1.rank() == Rank.ACE && card3.rank() == Rank.ACE)
            .build();
    }

    // SPLIT FEJLESZTÉSHEZ
    public GameState initializeNewRound(GameState state) {
        // 1. Kör állapotának nullázása
        GameState currentGameState = gameStateManager.resetRoundState(state)
            .toBuilder()
            .isRoundActive(true)
            .build();

        // ----------------------------------------------------
        // 2. KÁRTYA OSZTÁSI LÁNC (Hardcoded lapokkal a játékosnak)
        // ----------------------------------------------------

        // JÁTÉKOS 1. LAPJA: FIX KING
        Card card1 = new Card(Suit.HEARTS, Rank.KING);
        // Frissítjük a state-et (a pakliból elvileg kiesik egy lap a konzisztencia miatt)
        currentGameState = gameStateManager.safeDealCard(currentGameState).newGameState();

        // OSZTÓ 1. LAPJA: Marad véletlen
        DealResultWithState deal2 = gameStateManager.safeDealCard(currentGameState);
        Card card2 = deal2.dealtCard();
        currentGameState = deal2.newGameState();

        // JÁTÉKOS 2. LAPJA: FIX QUEEN (vagy King, a lényeg hogy 10-es érték)
        Card card3 = new Card(Suit.DIAMONDS, Rank.QUEEN);
        currentGameState = gameStateManager.safeDealCard(currentGameState).newGameState();

        // OSZTÓ 2. LAPJA: Marad véletlen
        DealResultWithState deal4 = gameStateManager.safeDealCard(currentGameState);
        Card card4 = deal4.dealtCard();
        currentGameState = deal4.newGameState();

        // ----------------------------------------------------
        // 3. ADATOK ELŐKÉSZÍTÉSE ÉS SZÁMÍTÁSOK
        // ----------------------------------------------------
        LinkedList<Card> playerStartHand = new LinkedList<>(List.of(card1, card3));
        int playerSum = handValueCalculator.calculateSum(playerStartHand);
        HandState playerHandState = handStateUpdater.updateHandState(playerSum, playerStartHand.size(), currentGameState.isWasSplitInRound());

        // Itt a canSplit true-t fog adni a King+Queen-re
        boolean isSplitPossible = gameRuleEngine.canSplit(playerStartHand);

        int nextCount = currentGameState.calculateNextHandCounter();
        String handId = String.format("P-%03d", nextCount);

        LinkedList<Card> dealerUnmaskedHand = new LinkedList<>(List.of(card2, card4));
        int dealerFullSum = handValueCalculator.calculateSum(dealerUnmaskedHand);
        HandState dealerHandState = handStateUpdater.updateHandState(dealerFullSum, dealerUnmaskedHand.size(), currentGameState.isWasSplitInRound());

        // ----------------------------------------------------
        // 4. IDEIGLENES OBJEKTUMOK ÉS BLACKJACK ELLENŐRZÉS
        // ----------------------------------------------------
        assert currentGameState.getPlayer() != null;
        PlayerHand tempPlayer = currentGameState.getPlayer().toBuilder()
            .id(handId)
            .hand(playerStartHand)
            .sum(playerSum)
            .handState(playerHandState.getValue())
            .canSplit(isSplitPossible) // Ez most TRUE lesz!
            .bet(currentGameState.getBet())
            .build();

        assert currentGameState.getDealerUnmasked() != null;
        DealerHandUnmasked tempDealerUnmasked = currentGameState.getDealerUnmasked().toBuilder()
            .hand(dealerUnmaskedHand)
            .sum(dealerFullSum)
            .build();

        GameState stateForCalculation = currentGameState.toBuilder()
            .player(tempPlayer)
            .dealerUnmasked(tempDealerUnmasked)
            .build();

        int initialOutcome = gameRuleEngine.initNatural21State(stateForCalculation);

        // ----------------------------------------------------
        // 5. VÉGLEGES OBJEKTUMOK ÖSSZESZERELÉSE
        // ----------------------------------------------------
        DealerHandUnmasked finalDealerUnmasked = tempDealerUnmasked.toBuilder()
            .handState(dealerHandState.getValue())
            .natural21(initialOutcome)
            .build();

        boolean canInsure = card4.rank() == Rank.ACE;
        int maskedStateForDealer = (initialOutcome == WinnerState.BLACKJACK_DEALER_WON.getValue())
            ? WinnerState.NONE.getValue()
            : initialOutcome;

        assert currentGameState.getDealerMasked() != null;
        DealerHandMasked updatedDealerMasked = currentGameState.getDealerMasked().toBuilder()
            .hand(new LinkedList<>(List.of(Card.createMaskedCard(), card4)))
            .canInsure(canInsure)
            .nat21(maskedStateForDealer)
            .build();

        // Visszatérünk
        return currentGameState.toBuilder()
            .player(tempPlayer)
            .dealerUnmasked(finalDealerUnmasked)
            .dealerMasked(updatedDealerMasked)
            .handCounter(nextCount)
            .natural21(initialOutcome)
            .isRoundActive(true)
            .aces(card1.rank() == Rank.ACE && card3.rank() == Rank.ACE) // Most False lesz
            .build();
    }

    // SPLIT + INSURANCE TESZTELÉSHEZ
    public GameState initializeNewRound1(GameState state) {
        // 1. Kör állapotának nullázása
        GameState currentGameState = gameStateManager.resetRoundState(state)
            .toBuilder()
            .isRoundActive(true)
            .build();

        // ----------------------------------------------------
        // 2. KÁRTYA OSZTÁSI LÁNC (Fixált lapokkal)
        // ----------------------------------------------------

        // JÁTÉKOS 1. LAPJA: FIX KING
        Card card1 = new Card(Suit.HEARTS, Rank.KING);
        currentGameState = gameStateManager.safeDealCard(currentGameState).newGameState();

        // OSZTÓ 1. LAPJA: Marad véletlen
        DealResultWithState deal2 = gameStateManager.safeDealCard(currentGameState);
        Card card2 = deal2.dealtCard();
        currentGameState = deal2.newGameState();

        // JÁTÉKOS 2. LAPJA: FIX QUEEN
        Card card3 = new Card(Suit.DIAMONDS, Rank.QUEEN);
        currentGameState = gameStateManager.safeDealCard(currentGameState).newGameState();

        // OSZTÓ 2. LAPJA: FIX ACE (Így az Insurance is aktiválódik)
        Card card4 = new Card(Suit.SPADES, Rank.ACE); // <-- FIX ÁSZ
        currentGameState = gameStateManager.safeDealCard(currentGameState).newGameState();

        // ----------------------------------------------------
        // 3. ADATOK ELŐKÉSZÍTÉSE ÉS SZÁMÍTÁSOK
        // ----------------------------------------------------
        LinkedList<Card> playerStartHand = new LinkedList<>(List.of(card1, card3));
        int playerSum = handValueCalculator.calculateSum(playerStartHand);
        HandState playerHandState = handStateUpdater.updateHandState(playerSum, playerStartHand.size(), currentGameState.isWasSplitInRound());

        boolean isSplitPossible = gameRuleEngine.canSplit(playerStartHand);

        int nextCount = currentGameState.calculateNextHandCounter();
        String handId = String.format("P-%03d", nextCount);

        LinkedList<Card> dealerUnmaskedHand = new LinkedList<>(List.of(card2, card4));
        int dealerFullSum = handValueCalculator.calculateSum(dealerUnmaskedHand);
        HandState dealerHandState = handStateUpdater.updateHandState(dealerFullSum, dealerUnmaskedHand.size(), currentGameState.isWasSplitInRound());

        // ----------------------------------------------------
        // 4. IDEIGLENES OBJEKTUMOK ÉS BLACKJACK ELLENŐRZÉS
        // ----------------------------------------------------
        assert currentGameState.getPlayer() != null;
        PlayerHand tempPlayer = currentGameState.getPlayer().toBuilder()
            .id(handId)
            .hand(playerStartHand)
            .sum(playerSum)
            .handState(playerHandState.getValue())
            .canSplit(isSplitPossible)
            .bet(currentGameState.getBet())
            .build();

        assert currentGameState.getDealerUnmasked() != null;
        DealerHandUnmasked tempDealerUnmasked = currentGameState.getDealerUnmasked().toBuilder()
            .hand(dealerUnmaskedHand)
            .sum(dealerFullSum)
            .build();

        GameState stateForCalculation = currentGameState.toBuilder()
            .player(tempPlayer)
            .dealerUnmasked(tempDealerUnmasked)
            .build();

        int initialOutcome = gameRuleEngine.initNatural21State(stateForCalculation);

        // ----------------------------------------------------
        // 5. VÉGLEGES OBJEKTUMOK ÖSSZESZERELÉSE
        // ----------------------------------------------------
        DealerHandUnmasked finalDealerUnmasked = tempDealerUnmasked.toBuilder()
            .handState(dealerHandState.getValue())
            .natural21(initialOutcome)
            .build();

        // Mivel card4 Ász, ez most TRUE lesz!
        boolean canInsure = card4.rank() == Rank.ACE;

        int maskedStateForDealer = (initialOutcome == WinnerState.BLACKJACK_DEALER_WON.getValue())
            ? WinnerState.NONE.getValue()
            : initialOutcome;

        assert currentGameState.getDealerMasked() != null;
        DealerHandMasked updatedDealerMasked = currentGameState.getDealerMasked().toBuilder()
            .hand(new LinkedList<>(List.of(Card.createMaskedCard(), card4)))
            .canInsure(canInsure) // TRUE
            .nat21(maskedStateForDealer)
            .build();

        return currentGameState.toBuilder()
            .player(tempPlayer)
            .dealerUnmasked(finalDealerUnmasked)
            .dealerMasked(updatedDealerMasked)
            .handCounter(nextCount)
            .natural21(initialOutcome)
            .isRoundActive(true)
            .aces(card1.rank() == Rank.ACE && card3.rank() == Rank.ACE)
            .build();
    }

    public GameState playerHit(GameState currentState) {
        if (!currentState.isRoundActive()) {
            return currentState;
        }

        DealResultWithState result = gameStateManager.safeDealCard(currentState);
        GameState deckUpdatedState = result.newGameState();
        Card card = result.dealtCard();

        PlayerHand playerHand = deckUpdatedState.getPlayer();
        assert playerHand != null;
        LinkedList<Card> newHandList = new LinkedList<>(playerHand.hand());
        newHandList.add(card);

        int newSum = handValueCalculator.calculateSum(newHandList);
        HandState calculatedHandState = handStateUpdater.updateHandState(newSum, newHandList.size(), deckUpdatedState.isWasSplitInRound());

        PlayerHand newPlayerHand = playerHand.toBuilder()
            .hand(newHandList)
            .sum(newSum)
            .handState(calculatedHandState.getValue())
            .build();

        return deckUpdatedState.toBuilder()
            .player(newPlayerHand)
            .build();
    }

    public GameState playerStand(GameState currentState) {
        if (!currentState.isRoundActive()) {
            return currentState;
        }

        PlayerHand playerHand = currentState.getPlayer();
        assert playerHand != null;
        int playerSum = playerHand.sum();
        HandState playerHandState = handStateUpdater.updateHandState(playerSum, playerHand.hand().size(), currentState.isWasSplitInRound());

        PlayerHand playerHandStated = playerHand.toBuilder()
            .stated(true)
            .handState(playerHandState.getValue())
            .build();

        GameState stateAfterPlayerStand = currentState.toBuilder()
            .player(playerHandStated)
            .build();
        logger.info("playerSum ({}).", playerSum);

        GameState finalDealerState;
        if (playerSum <= 21) {
            finalDealerState = gameStateManager.processDealerDrawing(stateAfterPlayerStand);
        } else {
            logger.info("Player BUSTED ({}). Dealer stays.", playerSum);
            finalDealerState = stateAfterPlayerStand;
        }
        assert finalDealerState.getDealerUnmasked() != null;
        logger.info("DEALERSUM    dealersum ({}).", finalDealerState.getDealerUnmasked().sum());
        int calculatedWinnerValue = gameRuleEngine.winnerStateUpdater(
            playerHandStated,
            finalDealerState.getDealerUnmasked()
        );

        return finalDealerState.toBuilder()
            .winner(calculatedWinnerValue)
            .build();
    }

    public TransactionResult calculateRewards(GameState gameState) {
        PlayerHand playerHand = gameState.getPlayer();
        DealerHandUnmasked dealerUnmasked = gameState.getDealerUnmasked();

        assert playerHand != null;
        int bet = playerHand.bet();

        int globalWinnerState = gameState.getWinner();
        int initialNatural21 = gameState.getNatural21();
        assert dealerUnmasked != null;
        int dealerUnmaskedNatural21 = dealerUnmasked.natural21();

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

        PlayerHand rewardedPlayerHand = playerHand.toBuilder()
            .bet(0)
            .build();

        GameState rewardedGameState = gameState.toBuilder()
            .bet(0)
            .betList(Collections.emptyList())
            .isRoundActive(!gameState.getPlayers().isEmpty())
            .player(rewardedPlayerHand)
            .build();

        return new TransactionResult(rewardedGameState, finalReward);
    }

    public GameState applyDoubleBet(GameState gameState, int originalBet) {
        int newTotalBet = gameState.getBet() + originalBet;

        assert gameState.getPlayer() != null;
        PlayerHand updatedPlayerHand = gameState.getPlayer().toBuilder()
            .bet(gameState.getPlayer().bet() + originalBet)
            .build();

        return gameState.toBuilder()
            .bet(newTotalBet)
            .player(updatedPlayerHand)
            .build();
    }

    public TransactionResult insuranceRequest(GameState state) {
        int currentBet = state.getBet();
        int insuranceCost = (int) Math.ceil(currentBet / 2.0);

        assert state.getDealerUnmasked() != null;
        int natural21 = state.getDealerUnmasked().natural21();
        boolean dealerHasBlackjack = (natural21 == WinnerState.BLACKJACK_DEALER_WON.getValue());

        GameState.GameStateBuilder stateBuilder = state.toBuilder();
        assert state.getPlayer() != null;
        PlayerHand.PlayerHandBuilder playerHandBuilder = state.getPlayer().toBuilder();

        int transactionAmount;

        if (dealerHasBlackjack) {
            // Ha az osztónak BJ van: A kör véget ér, a biztosítás kifizeti az eredeti tétet
            stateBuilder
                .bet(0)
                .isRoundActive(false)
                .winner(WinnerState.DEALER_WON.getValue());

            playerHandBuilder.bet(0);

            // A kifizetés mértéke (eredeti tétet visszakapja)
            transactionAmount = currentBet;
        } else {
            // Ha nincs BJ: A játék megy tovább, de a biztosítás ára elveszett
            transactionAmount = -insuranceCost;
        }

        GameState newState = stateBuilder
            .player(playerHandBuilder.build())
            .build();

        // Visszaadjuk az új állapotot és a pénzügyi változást (MENTÉS NÉLKÜL)
        return new TransactionResult(newState, transactionAmount);
    }

    // --- SPLIT ---
    public GameState splitHand(GameState oldState) {
        PlayerHand oldPlayerHand = oldState.getPlayer();

        if (oldPlayerHand == null || !oldPlayerHand.canSplit() || oldState.getPlayers().size() > 3) {
            return oldState;
        }

        Card cardToSplit = oldPlayerHand.hand().get(1);

        PlayerHand primaryHandNewState = oldPlayerHand.toBuilder()
            .hand(List.of(oldPlayerHand.hand().getFirst()))
            .build();

        GameState updatedState = oldState.toBuilder()
            .player(primaryHandNewState)
            .splitReq(oldState.calculateNewSplitReq(1))
            .wasSplitInRound(true)
            .build();

        GameState stateAfterFirstDeal = gameStateManager.dealSplitCard(updatedState, true, null);
        //logger.info("+++++++++++ split completed. playersIndex: {}", finalState.getPlayersIndex());
        return gameStateManager.dealSplitCard(stateAfterFirstDeal, false, cardToSplit);
    }

    public GameState addToPlayersListByStand(GameState oldState) {
        PlayerHand playerHand = oldState.getPlayer();

        // Ellenőrizzük, hogy vannak-e még aktív kezek
        boolean isActive = gameStateManager.areStatedFalseHands(oldState.getPlayers());
        //logger.info(">>>> isActive: {}", isActive);

        if (isActive) {
            // Garantáljuk, hogy van mit frissíteni
            if (playerHand == null) return oldState;

            // 1. Új PlayerHand állapot (Immutable update)
            PlayerHand updatedPlayerHand = playerHand.toBuilder()
                .stated(true)
                .build();

            // 2. Új Map-ek létrehozása a régiek alapján
            Map<String, PlayerHand> newPlayersMap = new HashMap<>(oldState.getPlayers());
            newPlayersMap.put(updatedPlayerHand.id(), updatedPlayerHand);

            Map<String, Boolean> newPlayersIndexMap = new HashMap<>(oldState.getPlayersIndex());
            newPlayersIndexMap.put(updatedPlayerHand.id(), true);

            return oldState.toBuilder()
                .player(updatedPlayerHand)
                .players(Collections.unmodifiableMap(newPlayersMap))
                .playersIndex(Collections.unmodifiableMap(newPlayersIndexMap))
                .build();

        } else {
            return oldState;
        }
    }

    public GameState addSplitPlayerToGame(GameState oldState) {
        GameState currentGameState = oldState;

        Map<String, Boolean> playersIndex = currentGameState.getPlayersIndex();
        Optional<String> nextIdOptional = gameStateManager.findNextActivePlayerId(playersIndex);

        if (nextIdOptional.isEmpty()) {
            return currentGameState;
        }

        String nextHandId = nextIdOptional.get();
        PlayerHand player = currentGameState.getPlayer();
        PlayerHand splitPlayer = currentGameState.getSplitPlayer();

        PlayerHand nextActiveHand = null;

        if (splitPlayer != null && splitPlayer.id() != null && splitPlayer.id().equals(nextHandId)) {
            nextActiveHand = splitPlayer;
        } else if (player != null && player.id().equals(nextHandId)) {
            nextActiveHand = player;
        } else if (currentGameState.getPlayers().containsKey(nextHandId)) {
            Map<String, PlayerHand> oldPlayersMap = currentGameState.getPlayers();

            Map<String, PlayerHand> newPlayersMap = new HashMap<>(oldPlayersMap);
            PlayerHand nextHand = newPlayersMap.remove(nextHandId);

            nextActiveHand = nextHand;

            currentGameState = currentGameState.toBuilder()
                .players(newPlayersMap)
                .splitPlayer(nextHand.toBuilder().build())
                .splitReq(currentGameState.calculateNewSplitReq(-1))
                .build();
        }

        if (nextActiveHand != null) {
            currentGameState = currentGameState.toBuilder()
                .player(nextActiveHand)
                .build();
        }

        PlayerHand playerToDealTo = currentGameState.getPlayer();

        assert playerToDealTo != null;
        if (playerToDealTo.hand().size() < 2) {
            DealResultWithState result = gameStateManager.safeDealCard(currentGameState);
            Card cardToAdd = result.dealtCard();

            currentGameState = result.newGameState();

            LinkedList<Card> newHandList = new LinkedList<>(playerToDealTo.hand());
            newHandList.add(cardToAdd);

            boolean canSplit = gameRuleEngine.canSplit(newHandList);
            int sum = handValueCalculator.calculateSum(newHandList);
            HandState playerState = handStateUpdater.updateHandState(sum, newHandList.size(), currentGameState.isWasSplitInRound());


            PlayerHand finalPlayerHand = playerToDealTo.toBuilder()
                .hand(newHandList)
                .sum(sum)
                .handState(playerState.getValue())
                .canSplit(canSplit)
                .build();

            currentGameState = currentGameState.toBuilder()
                .player(finalPlayerHand)
                .build();
        }

        return currentGameState;
    }

    public GameState addPlayerFromPlayers(GameState currentGameState) {
        Map<String, PlayerHand> playersMap = currentGameState.getPlayers();

        if (playersMap == null || playersMap.isEmpty()) {
            return currentGameState;
        }

        Map.Entry<String, PlayerHand> firstEntry = playersMap.entrySet().iterator().next();
        String firstId = firstEntry.getKey();
        PlayerHand extractedHand = firstEntry.getValue();
        //logger.info(" A PLAYER addPlayerFromPlayers {}).", extractedHand);
        Map<String, PlayerHand> newPlayersMap = new LinkedHashMap<>(playersMap);
        newPlayersMap.remove(firstId);

        return currentGameState.toBuilder()
            .player(extractedHand)
            .players(newPlayersMap)
            .build();
    }

    // --- RESET ---
    public GameState restartGame(GameState state) {
        GameState resetGameState = gameStateManager.resetRoundState(state);

        return resetGameState.toBuilder()
            .bet(0)
            .deck(Deck.createNewDeck())
            .build();
    }

    // --- RECORDS ---
    public record DealResultWithState(Card dealtCard, GameState newGameState) {
    }

    public record TransactionResult(GameState gameState, int transactionResult) {
    }
}
