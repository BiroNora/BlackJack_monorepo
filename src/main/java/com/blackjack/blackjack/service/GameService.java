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

    public GameState placeBet(Optional<GameState> gameStateOptional, int betAmount) {
        GameState oldState = gameStateManager.getGameStateOrThrow(gameStateOptional);
        logger.info("Az aktuálisan betöltött GameState bet mezője (oldState.getBet()): {}", oldState.getBet());
        logger.info("Az új tét, amit elhelyeztek (betAmount): {}", betAmount);
        List<Integer> oldBetList = new LinkedList<>(oldState.getBetList() != null ? oldState.getBetList() : new ArrayList<>()); // Másolás!
        List<Integer> newBetList = new LinkedList<>(oldBetList);
        newBetList.add(betAmount);
        int newTotalBet = oldState.getBet() + betAmount;
        PlayerHand oldPlayerHand = oldState.getPlayer();

        int newPlayerHandBet = oldPlayerHand.bet() + betAmount;
        PlayerHand updatedPlayerHand = oldPlayerHand.toBuilder()
            .bet(newPlayerHandBet)
            .build();

        GameState newState = oldState.toBuilder()
            .bet(newTotalBet)
            .betList(newBetList)
            .player(updatedPlayerHand)
            .build();

        gameStateService.saveGameState(newState);
        return newState;
    }

    public int retakeBet(Optional<GameState> gameStateOptional) {
        GameState oldState = gameStateManager.getGameStateOrThrow(gameStateOptional);
        List<Integer> betList = new LinkedList<>(oldState.getBetList()); // Fontos: Clone a List-ről!

        if (betList.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BAD REQUEST");
        }

        int amountToReturn = betList.remove(betList.size() - 1);
        int newTotalBet = oldState.getBet() - amountToReturn;

        GameState.GameStateBuilder newStateBuilder = oldState.toBuilder()
            .bet(newTotalBet)
            .betList(betList);

        if (betList.isEmpty()) {
            newStateBuilder.isRoundActive(false);
        }

        GameState newState = newStateBuilder.build();
        gameStateService.saveGameState(newState);

        return amountToReturn * -1;
    }

    public GameState createNewDeck(Optional<GameState> gameStateOptional) {
        GameState oldState = gameStateManager.getGameStateOrThrow(gameStateOptional);
        Deck newDeck = deckService.createNewDeck();

        GameState newState = oldState.toBuilder()
            .deck(newDeck)
            .build();

        logger.info("+++++++++++ createNewDeck {}", newState.getDeck());
        return newState;
    }

    public DealResultWithState safeDealCard(Optional<GameState> gameStateOptional) {
        // Csak a kezdeti állapotot kérjük le, ez lesz az immutable alap
        GameState currentGameState = gameStateManager.getGameStateOrThrow(gameStateOptional);
        Deck deck = currentGameState.getDeck();
        GameState stateToWorkWith = currentGameState; // Ezt a referenciát frissítjük az új Deck-kel

        // 1. Dekkellenőrzés és új dekk generálása, ha szükséges
        if (deck == null || deck.getDeckLength() == 0) {
            logger.info("--- DECK STATUS: Deck was NULL or empty. Initializing new deck.");

            // 🛑 HIBÁNAK KORRIGÁLÁSA: Ha a createNewDeck ment, ez a hívás elrontja az állapotot!
            // A createNewDeck(Optional) hívás helyett az aktuális állapotot kellene frissíteni egy új Deck-kel,
            // de az Ön eredeti kódja így volt megírva, ezért meghagyom, de tudja: ez gyanús!

            // Ha createNewDeck menti az állapotot, akkor ez a hívás rossz!
            stateToWorkWith = createNewDeck(Optional.of(currentGameState));
        }

        // Frissítjük a lokális deck referenciát, ha az állapot változott
        deck = stateToWorkWith.getDeck();

        logger.info("++++++++++ safeDealCard deck size before deal: {}", deck.getDeckLength());

        try {
            // 2. Laposztás (Immutable: lap és ÚJ Deck állapot)
            DealResult result = deckService.dealCard(deck);

            Card dealtCard = result.dealtCard();
            Deck newDeckState = result.newDeck();

            // 3. GameState frissítése az ÚJ Deck-kel (nem a teljes GameState-t frissíti a DeckService)
            // Azon az állapoton dolgozunk tovább, ami az 1. pontban létrejött (stateToWorkWith)
            GameState newState = stateToWorkWith.toBuilder()
                .deck(newDeckState)
                .build();

            // 🛑 HIBA JAVÍTÁSA: A mentés ELTÁVOLÍTVA. Ezt a hívó metódus (playerHit) végzi el.
            // gameStateService.saveGameState(newState);

            logger.debug("--- DEAL SUCCESS: Dealt {}. Remaining size: {}", dealtCard, newDeckState.getDeckLength());

            return new DealResultWithState(dealtCard, newState);

        } catch (IllegalStateException e) {
            // 4. CATCH BLOKK (Újrakeverés)
            logger.warn("!!! DECK EXHAUSTED: IllegalStateException caught. Starting automatic reshuffle/new deck.");

            // Az Ön eredeti hibás logikája:
            // GameState newGameState = createNewDeck(gameStateOptional); // EREDETI HÍVÁS

            // 🛑 HIBA JAVÍTÁSA: Itt is a currentGameState-et kellene küldeni, nem Optional-t
            // De a createNewDeck metódus MENTÉS miatti hívási mintáját követem:
            GameState newGameState = createNewDeck(Optional.of(currentGameState));
            Deck newDeck = newGameState.getDeck();

            // Laposztás az új pakliból
            DealResult result = deckService.dealCard(newDeck);

            Card dealtCard = result.dealtCard();
            Deck finalDeckState = result.newDeck();

            // GameState frissítése az új lappal és a frissített paklival
            GameState finalState = newGameState.toBuilder()
                .deck(finalDeckState)
                .build();

            // 🛑 HIBA JAVÍTÁSA: A mentés ELTÁVOLÍTVA. Ezt a hívó metódus (playerHit) végzi el.
            // gameStateService.saveGameState(finalState);

            logger.info("--- DEAL RESHUFFLE SUCCESS: Dealt {} from new deck. Remaining size: {}", dealtCard, finalDeckState.getDeckLength());

            return new DealResultWithState(dealtCard, finalState);
        }
    }

    public GameState initializeNewRound(Optional<GameState> gameStateOptional) {
        // 1. Kezdeti állapot lekérése
        GameState initialGameState = gameStateManager.getGameStateOrThrow(gameStateOptional);

        // 2. Kör állapotának nullázása (gameStateManager.resetRoundState-nek is immutábilisnak kell lennie!)
        GameState currentGameState = gameStateManager.resetRoundState(initialGameState)
            .toBuilder()
            .isRoundActive(true) // immutábilis hívás a .toBuilder().build() láncban
            .build();

        // ----------------------------------------------------
        // 3. KÁRTYA OSZTÁSI LÁNC (State Chaining)
        // ----------------------------------------------------

        // Kártya 1 (Player 1)
        DealResultWithState deal1 = safeDealCard(Optional.of(currentGameState));
        Card card1 = deal1.dealtCard();
        currentGameState = deal1.newGameState(); // Az állapot frissül az új Deckkel!

        // Kártya 2 (Dealer 1)
        DealResultWithState deal2 = safeDealCard(Optional.of(currentGameState));
        Card card2 = deal2.dealtCard();
        currentGameState = deal2.newGameState(); // Az állapot frissül az új Deckkel!

        // Kártya 3 (Player 2)
        DealResultWithState deal3 = safeDealCard(Optional.of(currentGameState));
        Card card3 = deal3.dealtCard();
        currentGameState = deal3.newGameState(); // Az állapot frissül az új Deckkel!

        // Kártya 4 (Dealer 2)
        DealResultWithState deal4 = safeDealCard(Optional.of(currentGameState));
        Card card4 = deal4.dealtCard();
        currentGameState = deal4.newGameState(); // Az állapot frissül az új Deckkel!

        // ----------------------------------------------------
// 4. PLAYER HAND BEÁLLÍTÁSA (Immutábilisan)
// ----------------------------------------------------
        int nextCount = currentGameState.calculateNextHandCounter();
        String handId = String.format("P-%03d", nextCount);
        LinkedList<Card> playerStartHand = new LinkedList<>(List.of(card1, card3));

// 🆕 SZÁMÍTÁSOK HOZZÁADÁSA:
        int playerSum = handValueCalculator.calculateSum(playerStartHand);
        HandState playerHandState = handStateUpdater.updateHandState(playerSum, playerStartHand.size());
        boolean isSplitPossible = gameRuleEngine.canSplit(playerStartHand);

        PlayerHand updatedPlayerHand = currentGameState.getPlayer().toBuilder()
            .id(handId)
            .hand(playerStartHand)
            .sum(playerSum)                           // ⬅️ BEÁLLÍTVA
            .handState(playerHandState.getValue())    // ⬅️ BEÁLLÍTVA
            .canSplit(isSplitPossible)
            .bet(currentGameState.getBet())
            .build();

// ----------------------------------------------------
// 5. DEALER HAND BEÁLLÍTÁSA (Immutábilisan)
// ----------------------------------------------------
        LinkedList<Card> dealerUnmaskedHand = new LinkedList<>(List.of(card2, card4));

// 🆕 SZÁMÍTÁSOK HOZZÁADÁSA:
        int dealerFullSum = handValueCalculator.calculateSum(dealerUnmaskedHand);
        HandState dealerHandState = handStateUpdater.updateHandState(dealerFullSum, dealerUnmaskedHand.size());

        int initialOutcome = gameRuleEngine.initNatural21State(currentGameState.toBuilder().player(updatedPlayerHand).build());

        DealerHandUnmasked updatedDealerUnmasked = currentGameState.getDealerUnmasked().toBuilder()
            .hand(dealerUnmaskedHand)
            .sum(dealerFullSum)                       // ⬅️ BEÁLLÍTVA
            .handState(dealerHandState.getValue())    // ⬅️ BEÁLLÍTVA
            .natural21(initialOutcome)
            .build();

        // ----------------------------------------------------
        // 6. DEALER MASKED BEÁLLÍTÁSA (Immutábilisan)
        // ----------------------------------------------------

        List<Card> maskedHand = List.of(card4);
        int dealerSum = handValueCalculator.calculateSum(maskedHand);
        boolean canInsure = card4.rank() == Rank.ACE;

        int maskedState = (initialOutcome == WinnerState.BLACKJACK_DEALER_WON.getValue())
            ? WinnerState.NONE.getValue()
            : initialOutcome;

        DealerHandMasked updatedDealerMasked = currentGameState.getDealerMasked().toBuilder()
            .hand(new LinkedList<>(List.of(Card.createMaskedCard(), card4)))
            .canInsure(canInsure)
            .nat21(maskedState)
            .build();

        // ----------------------------------------------------
        // 7. VÉGSŐ ÁLLAPOT LÉTREHOZÁSA ÉS MENTÉSE
        // ----------------------------------------------------

        GameState finalGameState = currentGameState.toBuilder()
            .player(updatedPlayerHand)
            .dealerUnmasked(updatedDealerUnmasked)
            .dealerMasked(updatedDealerMasked)
            .handCounter(nextCount)
            .natural21(initialOutcome)
            .isRoundActive(true)
            .aces(card1.rank() == Rank.ACE && card3.rank() == Rank.ACE) // Már lehet, hogy nem szükséges, ha a PlayerHand tudja
            .build();

        gameStateService.saveGameState(finalGameState);
        logger.info("+++++++++++ finalGameState {}", finalGameState);
        return finalGameState;
    }

    public GameState playerHit(Optional<GameState> gameStateOptional) {
        GameState oldState = gameStateManager.getGameStateOrThrow(gameStateOptional);
        logger.info("MMMMMMMMM newHandListElsoAllapot{}", oldState);
        if (!oldState.isRoundActive()) {
            return oldState;
        }

        // 1. Laposztás
        DealResultWithState result = safeDealCard(gameStateOptional);
        GameState deckUpdatedState = result.newGameState();

        PlayerHand oldPlayerHand = deckUpdatedState.getPlayer();

        // 2. Kézi lista frissítése (IMMUTABLE)
        assert oldPlayerHand != null;
        LinkedList<Card> newHandList = new LinkedList<>(oldPlayerHand.hand());
        newHandList.add(result.dealtCard());
        logger.info("MMMMMMMMM newHandList{}", newHandList);
        // 3. KÖTELEZŐ SZÁMÍTÁSOK (a rekordok hiánya miatt)
        int newSum = handValueCalculator.calculateSum(newHandList);
        HandState calculatedHandState = handStateUpdater.updateHandState(newSum, newHandList.size());

        boolean isRoundOverForPlayer = calculatedHandState != HandState.UNDER_21;

        // 4. PlayerHand építése a KISZÁMÍTOTT ÉRTÉKEKKEL
        PlayerHand newPlayerHand = oldPlayerHand.toBuilder()
            .hand(newHandList)
            .sum(newSum)
            .handState(calculatedHandState.getValue())
            .stated(isRoundOverForPlayer)
            .build();

        // 5. GameState frissítése
        GameState newState = deckUpdatedState.toBuilder()
            .player(newPlayerHand)
            .build();

        // 6. KÖRLEZÁRÁS ELLENŐRZÉSE (Ha Bust vagy 21, lezárjuk a játékos körét)
        if (isRoundOverForPlayer) {
            logger.info("Játékos Bust (Pont: {}) vagy 21. Elindítjuk az osztó logikáját.", newSum);

            // 🛑 Ha van külön metódusa a Dealer logikának, itt kell hívni!
            // Pl.: newState = gameRuleEngine.processDealerTurn(newState);

            // Minimum követelmény: Kör aktivitásának lezárása
            // Ha a processDealerTurn/gameRuleEngine nem létezik, és csak a körlezárás a cél:
            // newState = newState.toBuilder().isRoundActive(false).build();
        }

        // 7. Végleges állapot mentése
        gameStateService.saveGameState(newState);
        return newState;
    }

    public GameState playerStand(Optional<GameState> gameStateOptional) {
        GameState oldState = gameStateManager.getGameStateOrThrow(gameStateOptional);

        if (!oldState.isRoundActive()) {
            return oldState;
        }

        // 1. Állapotfrissítés: A játékos állt (Stand)
        // A PlayerHand sum-ot és handState-t az utolsó Hit/Stand előtt frissíteni kellett.
        PlayerHand oldPlayerHand = oldState.getPlayer();

        // Feltételezve, hogy a 'getSum' most már a tárolt mezőt adja vissza:
        int playerSum = oldPlayerHand.sum();

        // A Stand-nek megfelelő állapot:
        HandState calculatedPlayerHandState = handStateUpdater.updateHandState(playerSum, oldPlayerHand.hand().size());

        PlayerHand playerHandStated = oldPlayerHand.toBuilder()
            .stated(true)
            .handState(calculatedPlayerHandState.getValue())
            .build();

        // Létrehozzuk az állapotot a Játékos Stand-je után
        GameState stateAfterPlayerStand = oldState.toBuilder()
            .player(playerHandStated)
            .build();

        // 2. Dealer körének lebonyolítása (Immábilis hurok)
        GameState dealerDrawState = processDealerDrawing(stateAfterPlayerStand);

        // 3. Eredmény megállapítása
        int calculatedWinnerValue = gameRuleEngine.winnerStateUpdater(dealerDrawState);

        // 4. Körlezárás és Végső Állapot Építése
        GameState finalState = dealerDrawState.toBuilder()
            .winner(calculatedWinnerValue)
            .isRoundActive(false) // ⬅️ KULCS: Lezárjuk a kört!
            .build();

        gameStateService.saveGameState(finalState);
        return finalState;
    }

// --------------------------------------------------------------------------------
// Segédmetódus a Dealer Immábilis Húzásához (A GameService-en belül)
// --------------------------------------------------------------------------------

    private GameState processDealerDrawing(GameState currentState) {

        // A Dealer húz addig, amíg a pontszám kisebb, mint 17.
        while (handValueCalculator.calculateSum(currentState.getDealerUnmasked().hand()) < 17) {

            // 1. Kártya Húzása (és a Deck frissítése)
            // (A safeDealCard a Deck-et frissíti és visszaadja a dealResult.newGameState-et.)
            DealResultWithState dealResult = safeDealCard(Optional.of(currentState));

            // 2. Kártya és Frissített GameState kinyerése
            Card newCard = dealResult.dealtCard();
            GameState deckUpdatedState = dealResult.newGameState();

            DealerHandUnmasked oldDealerHand = deckUpdatedState.getDealerUnmasked();

            // 3. Dealer kéz IMMUTABILIS frissítése
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

        int bet = playerHand.bet();

        int globalWinnerState = gameState.getWinner();
        int initialNatural21 = gameState.getNatural21();
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

    public GameState doubleRequest(GameState gameState, int currentBet) {
        int doubledBet = currentBet * 2;

        PlayerHand oldPlayerHand = gameState.getPlayer();
        PlayerHand newPlayerHand = oldPlayerHand.toBuilder()
            .bet(doubledBet)
            .build();

        GameState newState = gameState.toBuilder()
            .player(newPlayerHand)
            .build();

        gameStateService.saveGameState(newState);
        return newState;
    }

    public TransactionResult insuranceRequest(Optional<GameState> gameStateOptional) {
        GameState oldState = gameStateManager.getGameStateOrThrow(gameStateOptional);

        int currentBet = oldState.getBet();
        double halfBet = (double) currentBet / 2.0;
        double roundedCost = Math.ceil(halfBet);


        int natural21 = oldState.getDealerUnmasked().natural21();

        GameState.GameStateBuilder newStateBuilder = oldState.toBuilder();
        PlayerHand.PlayerHandBuilder newPlayerHandBuilder = oldState.getPlayer().toBuilder();

        if (natural21 == WinnerState.BLACKJACK_DEALER_WON.getValue()) {
            newStateBuilder
                .bet(0)
                .isRoundActive(false)
                .winner(WinnerState.DEALER_WON.getValue());

            newPlayerHandBuilder.bet(0);
        }

        GameState newState = newStateBuilder
            .player(newPlayerHandBuilder.build())
            .build();

        gameStateService.saveGameState(newState);

        int insCost = natural21 == WinnerState.BLACKJACK_DEALER_WON.getValue() ? currentBet : (int) roundedCost * -1;

        return new TransactionResult(newState, insCost);
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
            DealResultWithState result = safeDealCard(Optional.of(currentGameState));
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

    public GameState restartGame(Optional<GameState> gameStateOptional) {
        GameState oldState = gameStateManager.getGameStateOrThrow(gameStateOptional);
        GameState resetGameState = gameStateManager.resetRoundState(oldState);

        // 🌟 Immutábilis frissítés
        GameState finalState = resetGameState.toBuilder()
            .bet(0)
            .build();

        gameStateService.saveGameState(finalState);

        return finalState;
    }

    public record DealResultWithState(Card dealtCard, GameState newGameState) {
    }

    public record TransactionResult(GameState gameState, int transactionResult) {
    }
}
