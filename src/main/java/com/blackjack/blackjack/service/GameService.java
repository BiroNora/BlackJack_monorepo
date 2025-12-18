package com.blackjack.blackjack.service;

import com.blackjack.blackjack.factory.PlayerHandFactory;
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
    private final PlayerHandFactory playerHandFactory;
    private final HandValueCalculator handValueCalculator;
    private final HandStateUpdater handStateUpdater;

    public GameService(GameStateService gameStateService, GameRuleEngine gameRuleEngine, GameStateManager gameStateManager, DeckService deckService, PlayerHandFactory playerHandFactory, HandValueCalculator handValueCalculator, HandStateUpdater handStateUpdater) {
        this.gameStateService = gameStateService;
        this.gameRuleEngine = gameRuleEngine;
        this.gameStateManager = gameStateManager;
        this.deckService = deckService;
        this.playerHandFactory = playerHandFactory;
        this.handValueCalculator = handValueCalculator;
        this.handStateUpdater = handStateUpdater;
    }

    public GameState placeBet(GameState oldState, int betAmount) {
        List<Integer> newBetList = new ArrayList<>(oldState.getBetList() != null ? oldState.getBetList() : List.of());
        newBetList.add(betAmount);

        int newTotalBet = oldState.getBet() + betAmount;
        assert oldState.getPlayer() != null;
        PlayerHand updatedPlayerHand = oldState.getPlayer().toBuilder()
            .bet(oldState.getPlayer().bet() + betAmount)
            .build();

        return oldState.toBuilder()
            .bet(newTotalBet)
            .betList(newBetList)
            .player(updatedPlayerHand)
            .build();
    }

    public RetakeResult retakeBet(GameState oldState) {
        if (oldState.getBetList() == null || oldState.getBetList().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NO_BET_TO_RETAKE");
        }

        List<Integer> betList = new ArrayList<>(oldState.getBetList());
        int amountToReturn = betList.removeLast();
        int newTotalBet = oldState.getBet() - amountToReturn;

        GameState.GameStateBuilder newStateBuilder = oldState.toBuilder()
            .bet(newTotalBet)
            .betList(betList);

        if (betList.isEmpty()) {
            newStateBuilder.isRoundActive(false);
        }

        return new RetakeResult(newStateBuilder.build(), amountToReturn);
    }

    public GameState createNewDeck(GameState state) {
        Deck newDeck = deckService.createNewDeck();
        logger.info("+++++++++++ createNewDeck {}", newDeck);
        return state.toBuilder().deck(newDeck).build();
    }

    public DealResultWithState safeDealCard(GameState state) {
        GameState workingState = (state.getDeck() == null || state.getDeckLen() == 0)
            ? createNewDeck(state)
            : state;

        try {
            DealResult result = deckService.dealCard(workingState.getDeck());

            GameState newState = workingState.toBuilder()
                .deck(result.newDeck())
                .build();

            return new DealResultWithState(result.dealtCard(), newState);

        } catch (IllegalStateException e) {
            logger.warn("!!! DECK EXHAUSTED: Reshuffling automatically.");

            GameState reshuffledState = createNewDeck(workingState);
            DealResult result = deckService.dealCard(reshuffledState.getDeck());

            GameState finalState = reshuffledState.toBuilder()
                .deck(result.newDeck())
                .build();

            return new DealResultWithState(result.dealtCard(), finalState);
        }
    }

    // VALÓS FG
    public GameState initializeNewRound1(GameState state) {
        // 1. Kör állapotának nullázása (resetRoundState immutábilis)
        GameState currentGameState = gameStateManager.resetRoundState(state)
            .toBuilder()
            .isRoundActive(true)
            .build();

        // ----------------------------------------------------
        // 2. KÁRTYA OSZTÁSI LÁNC (Tisztán objektumokkal)
        // ----------------------------------------------------
        DealResultWithState deal1 = safeDealCard(currentGameState);
        Card card1 = deal1.dealtCard();
        currentGameState = deal1.newGameState();

        DealResultWithState deal2 = safeDealCard(currentGameState);
        Card card2 = deal2.dealtCard();
        currentGameState = deal2.newGameState();

        DealResultWithState deal3 = safeDealCard(currentGameState);
        Card card3 = deal3.dealtCard();
        currentGameState = deal3.newGameState();

        DealResultWithState deal4 = safeDealCard(currentGameState);
        Card card4 = deal4.dealtCard();
        currentGameState = deal4.newGameState();

        // ----------------------------------------------------
        // 3. ADATOK ELŐKÉSZÍTÉSE ÉS SZÁMÍTÁSOK
        // ----------------------------------------------------
        LinkedList<Card> playerStartHand = new LinkedList<>(List.of(card1, card3));
        int playerSum = handValueCalculator.calculateSum(playerStartHand);
        HandState playerHandState = handStateUpdater.updateHandState(playerSum, playerStartHand.size());
        boolean isSplitPossible = gameRuleEngine.canSplit(playerStartHand);

        int nextCount = currentGameState.calculateNextHandCounter();
        String handId = String.format("P-%03d", nextCount);

        LinkedList<Card> dealerUnmaskedHand = new LinkedList<>(List.of(card2, card4));
        int dealerFullSum = handValueCalculator.calculateSum(dealerUnmaskedHand);
        HandState dealerHandState = handStateUpdater.updateHandState(dealerFullSum, dealerUnmaskedHand.size());

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

    // SPLITHEZ
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
        currentGameState = safeDealCard(currentGameState).newGameState();

        // OSZTÓ 1. LAPJA: Marad véletlen
        DealResultWithState deal2 = safeDealCard(currentGameState);
        Card card2 = deal2.dealtCard();
        currentGameState = deal2.newGameState();

        // JÁTÉKOS 2. LAPJA: FIX QUEEN (vagy King, a lényeg hogy 10-es érték)
        Card card3 = new Card(Suit.DIAMONDS, Rank.QUEEN);
        currentGameState = safeDealCard(currentGameState).newGameState();

        // OSZTÓ 2. LAPJA: Marad véletlen
        DealResultWithState deal4 = safeDealCard(currentGameState);
        Card card4 = deal4.dealtCard();
        currentGameState = deal4.newGameState();

        // ----------------------------------------------------
        // 3. ADATOK ELŐKÉSZÍTÉSE ÉS SZÁMÍTÁSOK
        // ----------------------------------------------------
        LinkedList<Card> playerStartHand = new LinkedList<>(List.of(card1, card3));
        int playerSum = handValueCalculator.calculateSum(playerStartHand);
        HandState playerHandState = handStateUpdater.updateHandState(playerSum, playerStartHand.size());

        // Itt a canSplit true-t fog adni a King+Queen-re
        boolean isSplitPossible = gameRuleEngine.canSplit(playerStartHand);

        int nextCount = currentGameState.calculateNextHandCounter();
        String handId = String.format("P-%03d", nextCount);

        LinkedList<Card> dealerUnmaskedHand = new LinkedList<>(List.of(card2, card4));
        int dealerFullSum = handValueCalculator.calculateSum(dealerUnmaskedHand);
        HandState dealerHandState = handStateUpdater.updateHandState(dealerFullSum, dealerUnmaskedHand.size());

        // ----------------------------------------------------
        // 4. IDEIGLENES OBJEKTUMOK ÉS BLACKJACK ELLENŐRZÉS
        // ----------------------------------------------------
        PlayerHand tempPlayer = currentGameState.getPlayer().toBuilder()
            .id(handId)
            .hand(playerStartHand)
            .sum(playerSum)
            .handState(playerHandState.getValue())
            .canSplit(isSplitPossible) // Ez most TRUE lesz!
            .bet(currentGameState.getBet())
            .build();

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

    public GameState playerHit(GameState currentState) {
        // 1. Ha a kör már nem aktív, ne csináljunk semmit
        if (!currentState.isRoundActive()) {
            return currentState;
        }

        // 2. Laposztás (A refaktorált, GameState-et váró metódussal)
        DealResultWithState result = safeDealCard(currentState);
        GameState deckUpdatedState = result.newGameState();
        Card card = result.dealtCard();

        // 3. Játékos kezének frissítése (Immutábilisan)
        PlayerHand playerHand = deckUpdatedState.getPlayer();
        assert playerHand != null;
        LinkedList<Card> newHandList = new LinkedList<>(playerHand.hand());
        newHandList.add(card);

        // 4. Számítások
        int newSum = handValueCalculator.calculateSum(newHandList);
        HandState calculatedHandState = handStateUpdater.updateHandState(newSum, newHandList.size());

        // Akkor állunk meg, ha 21 vagy felette (Bust) vagyunk
        boolean isRoundOverForPlayer = calculatedHandState != HandState.UNDER_21;

        // 5. PlayerHand objektum felépítése
        PlayerHand newPlayerHand = playerHand.toBuilder()
            .hand(newHandList)
            .sum(newSum)
            .handState(calculatedHandState.getValue())
            .stated(isRoundOverForPlayer)
            .build();

        // 6. Új GameState létrehozása
        GameState newState = deckUpdatedState.toBuilder()
            .player(newPlayerHand)
            .build();

        // 7. Körlezárás ellenőrzése
        if (isRoundOverForPlayer) {
            logger.info("Játékos megállt vagy besokallt (Pont: {}).", newSum);
            // Itt még nem hívunk Dealer logikát, csak jelezzük a lezárást a hívónak,
            // vagy ha van processDealerTurn, azt is ide lehet láncolni.
        }

        return newState; // MENTÉS NÉLKÜL
    }

    public GameState playerStand(GameState currentState) {
        if (!currentState.isRoundActive()) {
            return currentState;
        }

        // 1. Állapotfrissítés: A játékos állt (Stand)
        PlayerHand oldPlayerHand = currentState.getPlayer();
        assert oldPlayerHand != null;
        int playerSum = oldPlayerHand.sum();
        HandState playerHandState = handStateUpdater.updateHandState(playerSum, oldPlayerHand.hand().size());

        PlayerHand playerHandStated = oldPlayerHand.toBuilder()
            .stated(true)
            .handState(playerHandState.getValue())
            .build();

        GameState stateAfterPlayerStand = currentState.toBuilder()
            .player(playerHandStated)
            .build();

        // 2. Dealer körének lebonyolítása (Ez is egy immutábilis lánc!)
        // Fontos: A processDealerDrawing metódusnak is GameState-et kell várnia és visszaadnia!
        GameState dealerDrawState = processDealerDrawing(stateAfterPlayerStand);

        // 3. Eredmény megállapítása
        int calculatedWinnerValue = gameRuleEngine.winnerStateUpdater(dealerDrawState);

        // 4. Körlezárás és Végső Állapot visszaküldése (MENTÉS NÉLKÜL)
        return dealerDrawState.toBuilder()
            .winner(calculatedWinnerValue)
            .isRoundActive(false) // Lezárjuk a kört
            .build();
    }

    private GameState processDealerDrawing(GameState currentState) {

        // A Dealer húz addig, amíg a pontszám kisebb, mint 17.
        while (true) {
            assert currentState.getDealerUnmasked() != null;
            if (!(handValueCalculator.calculateSum(currentState.getDealerUnmasked().hand()) < 17)) break;

            // 1. Kártya Húzása (és a Deck frissítése)
            // (A safeDealCard a Deck-et frissíti és visszaadja a dealResult.newGameState-et.)
            DealResultWithState dealResult = safeDealCard(currentState);

            // 2. Kártya és Frissített GameState kinyerése
            Card newCard = dealResult.dealtCard();
            GameState deckUpdatedState = dealResult.newGameState();

            DealerHandUnmasked oldDealerHand = deckUpdatedState.getDealerUnmasked();

            // 3. Dealer kéz IMMUTABILIS frissítése
            assert oldDealerHand != null;
            LinkedList<Card> newDealerHandList = new LinkedList<>(oldDealerHand.hand());
            newDealerHandList.add(newCard);

            int newDealerSum = handValueCalculator.calculateSum(newDealerHandList);
            HandState newDealerHandState = handStateUpdater.updateHandState(newDealerSum, newDealerHandList.size());

            DealerHandUnmasked newDealerHand = oldDealerHand.toBuilder()
                .hand(newDealerHandList)
                .sum(newDealerSum)
                .handState(newDealerHandState.getValue())
                .build();

            // 4. Frissítjük a 'currentState'-et a HÚZOTT KÁRTYÁVAL (az új DealerHand-del)
            currentState = deckUpdatedState.toBuilder()
                .dealerUnmasked(newDealerHand)
                .build();

            // Ha Bust-olt, a ciklus leáll a következő iterációban.
            if (newDealerSum > 21) {
                break;
            }
        }
        return currentState; // A Dealer húzásának végleges állapota
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
            .isRoundActive(false)
            .player(rewardedPlayerHand)
            .build();
        gameStateService.saveGameState(rewardedGameState);
        logger.info(">>>>>> rewardedGameState {}",
            rewardedGameState);

        return new TransactionResult(rewardedGameState, finalReward);
    }

    public GameState applyDoubleBet(GameState gameState, int originalBet) {
        // A Double Down lényege: az eredeti tétet még egyszer hozzáadjuk (összesen 2x)
        int newTotalBet = gameState.getBet() + originalBet;

        assert gameState.getPlayer() != null;
        PlayerHand updatedPlayerHand = gameState.getPlayer().toBuilder()
            .bet(gameState.getPlayer().bet() + originalBet)
            .build();

        // Visszaadjuk az új állapotot, mentés nélkül
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

    public GameState splitHand(Optional<GameState> gameStateOptional) {
        GameState oldState = gameStateManager.getGameStateOrThrow(gameStateOptional);
        PlayerHand oldPlayerHand = oldState.getPlayer();

        if (!oldPlayerHand.canSplit() || oldState.getPlayers() == null || oldState.getPlayers().size() > 3) {
            return oldState;
        }

        List<Card> oldHandCards = oldPlayerHand.hand();

        Card cardToSplit = oldHandCards.get(1);

        LinkedList<Card> primaryHandCards = new LinkedList<>(oldHandCards);
        primaryHandCards.remove(1);

        PlayerHand primaryHandNewState = oldPlayerHand.toBuilder()
            .hand(primaryHandCards)
            .build();

        GameState state1_PrimaryHandUpdated = oldState.toBuilder()
            .player(primaryHandNewState)
            .build();

        GameState state2_CardDealt = gameStateManager.dealSplitCard(state1_PrimaryHandUpdated, true, null);

        int newHandCounter = state2_CardDealt.calculateNextHandCounter();
        String secondPlayerId = String.format("P-%03d", newHandCounter);

        PlayerHand secondPlayerTemplate = playerHandFactory.createEmptyHand();

        PlayerHand secondPlayer = secondPlayerTemplate.toBuilder()
            .id(secondPlayerId)
            .hand(new LinkedList<>(List.of(cardToSplit)))
            .bet(state2_CardDealt.getBet())
            .build();

        Map<String, PlayerHand> newPlayersMap = new LinkedHashMap<>(state2_CardDealt.getPlayers());
        newPlayersMap.put(secondPlayer.id(), secondPlayer);

        int newSplitReq = state2_CardDealt.calculateNewSplitReq(1);

        GameState finalState = state2_CardDealt.toBuilder()
            .handCounter(newHandCounter)
            .players(newPlayersMap)
            .splitReq(newSplitReq)
            .build();

        gameStateService.saveGameState(finalState);

        return finalState;
    }

    public GameState addToPlayersListByStand(Optional<GameState> gameStateOptional) {
        GameState oldState = gameStateManager.getGameStateOrThrow(gameStateOptional);
        PlayerHand playerHand = oldState.getPlayer();

        boolean isActive = gameStateManager.areStatedFalseHands(oldState.getPlayers());
        logger.info(">>>>y isActive: {}", isActive);

        if (isActive) {
            PlayerHand updatedPlayerHand = playerHand.toBuilder()
                .stated(true)
                .build();

            Map<String, PlayerHand> newPlayersMap = new HashMap<>(oldState.getPlayers());
            newPlayersMap.put(updatedPlayerHand.id(), updatedPlayerHand);

            Map<String, Boolean> newPlayersIndexMap = new HashMap<>(oldState.getPlayersIndex());
            String ID = updatedPlayerHand.id();
            newPlayersIndexMap.put(ID, true);

            GameState newState = oldState.toBuilder()
                .player(updatedPlayerHand)
                .players(newPlayersMap)
                .playersIndex(newPlayersIndexMap)
                .build();

            gameStateService.saveGameState(newState);
            return newState;

        } else {
            return oldState;
        }
    }

    public GameState addSplitPlayerToGame(Optional<GameState> gameStateOptional) {
        GameState oldState = gameStateManager.getGameStateOrThrow(gameStateOptional);
        GameState currentGameState = oldState; // Ezt a referenciát láncoljuk!

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
            PlayerHand nextHand = newPlayersMap.remove(nextHandId); // Mutábilis Map művelet a KLÓNON!

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

        if (playerToDealTo.hand().size() < 2) {
            DealResultWithState result = safeDealCard(currentGameState);
            Card cardToAdd = result.dealtCard();

            currentGameState = result.newGameState();

            LinkedList<Card> newHandList = new LinkedList<>(playerToDealTo.hand());
            newHandList.add(cardToAdd);

            boolean canSplit = gameRuleEngine.canSplit(newHandList);

            PlayerHand finalPlayerHand = playerToDealTo.toBuilder()
                .hand(newHandList)
                .canSplit(canSplit)
                .build();

            currentGameState = currentGameState.toBuilder()
                .player(finalPlayerHand)
                .build();
        }

        gameStateService.saveGameState(currentGameState);
        return currentGameState;
    }

    public GameState addPlayerFromPlayers(Optional<GameState> gameStateOptional) {
        // A GameState lekérése (ez immutable objektum)
        GameState currentGameState = gameStateManager.getGameStateOrThrow(gameStateOptional);

        // 1. Állapot Ellenőrzése és Korai Kilépés
        // Mivel a players Map immutable (a @Value miatt), biztonságosan lekérdezhető.
        Map<String, PlayerHand> playersMap = currentGameState.getPlayers();

        if (playersMap == null || playersMap.isEmpty()) {
            return currentGameState; // Visszaadjuk a változatlan állapotot
        }

        // 2. Az első elem azonosítása
        Map.Entry<String, PlayerHand> firstEntry = playersMap.entrySet().iterator().next();
        String firstId = firstEntry.getKey();
        PlayerHand extractedHand = firstEntry.getValue(); // Csak az értéket vesszük ki, nem módosítjuk a Map-et még!

        // 3. Létrehozzuk az ÚJ, frissített Map-et
        // Ezt a Map-et KELL MÓDOSÍTANI, nem az eredeti GameState-ben lévőt!
        Map<String, PlayerHand> newPlayersMap = new LinkedHashMap<>(playersMap);
        newPlayersMap.remove(firstId); // ⬅️ Itt történik a módosítás a MÁSOLATON!

        // 4. IMMUTABILIS ÁLLAPOT FRISSÍTÉS (ÚJ GameState gyártása)
        // Ezt a lépést hívjuk "non-destructive update"-nek.
        GameState newGameState = currentGameState.toBuilder()
            .player(extractedHand) // Beállítjuk az új PlayerHand-et
            .players(newPlayersMap) // Beállítjuk a frissített Map másolatot
            .build(); // Létrehozzuk a kész, új állapotot

        // 5. Elmentjük az ÚJ állapotot és visszatérünk vele
        gameStateService.saveGameState(newGameState);

        return newGameState;
    }

    public GameState restartGame(GameState state) {
        GameState resetGameState = gameStateManager.resetRoundState(state);

        return resetGameState.toBuilder()
            .bet(0)
            .isRoundActive(false)
            .natural21(0)
            .winner(0)
            .build();
    }

    public record RetakeResult(GameState newState, int amountReturned) {
    }

    public record DealResultWithState(Card dealtCard, GameState newGameState) {
    }

    public record TransactionResult(GameState gameState, int transactionResult) {
    }


}
