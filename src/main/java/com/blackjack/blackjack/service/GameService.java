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

    public GameService(GameStateService gameStateService, GameRuleEngine gameRuleEngine, GameStateManager gameStateManager, DeckService deckService, PlayerHandFactory playerHandFactory) {
        this.gameStateService = gameStateService;
        this.gameRuleEngine = gameRuleEngine;
        this.gameStateManager = gameStateManager;
        this.deckService = deckService;
        this.playerHandFactory = playerHandFactory;
    }

    public record DealResultWithState(Card dealtCard, GameState newGameState) {
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

        // PlayerHand tét: Hozzáadjuk az új tétet a PlayerHand jelenlegi tétjéhez.
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

        gameStateService.saveGameState(newState);
        logger.info("+++++++++++ createNewDeck {}", newState.getDeck());
        return newState;
    }

    public DealResultWithState safeDealCard(Optional<GameState> gameStateOptional) {
        GameState gameState = gameStateManager.getGameStateOrThrow(gameStateOptional);
        Deck deck = gameState.getDeck();

        if (deck == null || deck.getDeckLength() == 0) {
            logger.info("--- DECK STATUS: Deck was NULL or empty. Initializing new deck.");
            gameState = createNewDeck(Optional.of(gameState));
            deck = gameState.getDeck();
        }

        logger.info("++++++++++ safeDealCard deck size before deal: {}", gameState.getDeck().getDeckLength());

        try {
            DealResult result = deckService.dealCard(deck);

            Card dealtCard = result.dealtCard();
            Deck newDeckState = result.newDeck();

            GameState newState = gameState.toBuilder()
                .deck(newDeckState)
                .build();

            gameStateService.saveGameState(newState);
            logger.debug("--- DEAL SUCCESS: Dealt {}. Remaining size: {}", dealtCard, newDeckState.getDeckLength());

            return new DealResultWithState(dealtCard, newState);

        } catch (IllegalStateException e) {
            logger.warn("!!! DECK EXHAUSTED: IllegalStateException caught. Starting automatic reshuffle/new deck.");

            GameState newGameState = createNewDeck(gameStateOptional);
            Deck newDeck = newGameState.getDeck();

            DealResult result = deckService.dealCard(newDeck);

            Card dealtCard = result.dealtCard();
            Deck finalDeckState = result.newDeck();

            GameState finalState = newGameState.toBuilder()
                .deck(finalDeckState)
                .build();

            gameStateService.saveGameState(finalState);
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

        String handId = currentGameState.generateNextHandId();
        LinkedList<Card> playerStartHand = new LinkedList<>(List.of(card1, card3));

        boolean isSplitPossible = gameRuleEngine.canSplit(playerStartHand);

        PlayerHand updatedPlayerHand = currentGameState.getPlayer().toBuilder()
            .id(handId)
            .hand(playerStartHand)
            .canSplit(isSplitPossible)
            .bet(currentGameState.getBet())
            .build();

        // ----------------------------------------------------
        // 5. DEALER HAND BEÁLLÍTÁSA (Immutábilisan)
        // ----------------------------------------------------

        LinkedList<Card> dealerUnmaskedHand = new LinkedList<>(List.of(card2, card4));
        int initialOutcome = gameRuleEngine.initNatural21State(currentGameState.toBuilder().player(updatedPlayerHand).build());

        DealerHandUnmasked updatedDealerUnmasked = currentGameState.getDealerUnmasked().toBuilder()
            .hand(dealerUnmaskedHand)
            .natural21(initialOutcome)
            .build();

        // ----------------------------------------------------
        // 6. DEALER MASKED BEÁLLÍTÁSA (Immutábilisan)
        // ----------------------------------------------------

        List<Card> maskedHand = List.of(card4);
        int dealerSum = HandValueCalculator.calculateSum(maskedHand);
        boolean canInsure = card4.rank() == Rank.ACE;

        int maskedState = (initialOutcome == WinnerState.BLACKJACK_DEALER_WON.getValue())
            ? WinnerState.NONE.getValue()
            : initialOutcome;

        DealerHandMasked updatedDealerMasked = currentGameState.getDealerMasked().toBuilder()
            .hand(new LinkedList<>(List.of(Card.createMaskedCard(), card4))) // Új lista létrehozása!
            .sum(dealerSum)
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
            .natural21(initialOutcome)
            .aces(card1.rank() == Rank.ACE && card3.rank() == Rank.ACE) // Már lehet, hogy nem szükséges, ha a PlayerHand tudja
            .build();

        gameStateService.saveGameState(finalGameState);
        logger.info("+++++++++++ deck {}", finalGameState.getDeck());
        return finalGameState;
    }

    public GameState playerHit(Optional<GameState> gameStateOptional) {
        GameState oldState = gameStateManager.getGameStateOrThrow(gameStateOptional);

        if (!oldState.isRoundActive()) {
            return oldState;
        }

        DealResultWithState result = safeDealCard(gameStateOptional);
        Card newCard = result.dealtCard();

        GameState currentState = result.newGameState();

        PlayerHand oldPlayerHand = currentState.getPlayer();

        LinkedList<Card> newHandList = new LinkedList<>(oldPlayerHand.hand());
        newHandList.add(newCard);

        PlayerHand newPlayerHand = oldPlayerHand.toBuilder()
            .hand(newHandList)
            .build();

        GameState newState = currentState.toBuilder()
            .player(newPlayerHand)
            .build();

        gameStateService.saveGameState(newState);
        return newState;
    }

    public GameState playerStand(Optional<GameState> gameStateOptional) {
        GameState oldState = gameStateManager.getGameStateOrThrow(gameStateOptional);
        int dealerSum = oldState.getDealerUnmasked().getSum();
        int playerSum = oldState.getPlayer().getSum();
        GameState currentState = oldState;

        if (playerSum <= 21) {
            // Kritikus: A dealer húzás itt még mindig a 'currentState'-et mutálja a while ciklus miatt,
            // de utána létrehozzuk az IMMUTÁBILIS végső állapotot.
            while (dealerSum < 17) {
                DealResultWithState result = safeDealCard(gameStateOptional);
                Card newCard = result.dealtCard();
                currentState.getDealerUnmasked().hand().add(newCard); // Mutáció (a safeDealCard a Deck-et is menti)
                dealerSum = currentState.getDealerUnmasked().getSum();
            }
        }

        currentState.getPlayer().getHandState();
        currentState.getDealerUnmasked().getHandState();

        int calculatedWinnerValue = gameRuleEngine.winnerStateUpdater(currentState);

        // 🌟 Immutábilis frissítés a setWinner helyett
        GameState finalState = currentState.toBuilder()
            .winner(calculatedWinnerValue)
            .build();

        gameStateService.saveGameState(finalState);
        return finalState;
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

        // 1. Betöltjük a régi állapotot (oldState)
        GameState oldState = gameStateManager.getGameStateOrThrow(gameStateOptional);
        PlayerHand oldPlayerHand = oldState.getPlayer();

        // 2. Pre-check: Ha nem lehetséges, visszatérünk a régi, változatlan állapottal
        if (!oldPlayerHand.canSplit() || oldState.getPlayers() == null || oldState.getPlayers().size() > 3) {
            return oldState;
        }

        // --- IMMUTABLE ÁLLAPOT KEZELÉSE ---

        // 3. Különválasztjuk a splitelendő lapot (Immutable List Manipuláció)
        List<Card> oldHandCards = oldPlayerHand.hand();

        // A kiválasztott lap (index 1)
        Card cardToSplit = oldHandCards.get(1);

        // Létrehozzuk az új kártyalistát az első kézhez (MÓDOSÍTHATÓ MÁSOLAT a remove-hoz)
        LinkedList<Card> primaryHandCards = new LinkedList<>(oldHandCards);
        primaryHandCards.remove(1);

        // 4. Frissítjük a PlayerHand objektumot az eltávolított lappal (Immutable)
        PlayerHand primaryHandNewState = oldPlayerHand.toBuilder()
            .hand(primaryHandCards)
            // Megtartjuk az összes többi beállítást (pl. tét)
            .build();

        // State 1: A GameState frissítve az első kéz új állapotával (egy lap hiányzik)
        GameState state1_PrimaryHandUpdated = oldState.toBuilder()
            .player(primaryHandNewState)
            .build();

        // 5. Osztunk egy lapot az első kéznek (DealCard hívás)
        // Ez a hívás frissíti a DECK-et, a PRIMARY HAND-et, és visszaadja az ÚJ GameState-et.
        GameState state2_CardDealt = gameStateManager.dealSplitCard(state1_PrimaryHandUpdated, true, null);

        // 6. Létrehozzuk a Második (Splitelt) Kézdarabot (Immutable)

        // 🚨 Kezeljük a Hand Counter inkrementálást (volt gameState.generateNextHandId())
        int newHandCounter = state2_CardDealt.getHandCounter() + 1;
        String secondPlayerId = String.format("P-%03d", newHandCounter);

        // Feltételezve, hogy létezik egy immutable template (playerHandFactory.createEmptyHand())
        PlayerHand secondPlayerTemplate = playerHandFactory.createEmptyHand();

        // Létrehozzuk a második PlayerHand objektumot
        PlayerHand secondPlayer = secondPlayerTemplate.toBuilder()
            .id(secondPlayerId)
            .hand(new LinkedList<>(List.of(cardToSplit))) // Az eltávolított lap az új kézben
            .bet(state2_CardDealt.getBet()) // Az aktuális tét
            // Feltételezve, hogy a többi mezőt a template kezeli
            .build();

        // 7. Frissítjük a players Map-et (Immutable Map Módosítás)

        // Másolatot készítünk az utolsó állás Map-jéről
        Map<String, PlayerHand> newPlayersMap = new LinkedHashMap<>(state2_CardDealt.getPlayers());
        newPlayersMap.put(secondPlayer.id(), secondPlayer);

        // 8. Kiszámoljuk az utolsó állapotváltozókat
        int newSplitReq = state2_CardDealt.getSplitReq() + 1;

        // 9. Végső GameState összeállítása
        GameState finalState = state2_CardDealt.toBuilder()
            .handCounter(newHandCounter) // 🚨 Frissítjük a Hand Counter-t
            .players(newPlayersMap) // Az új Map-et beállítjuk
            .splitReq(newSplitReq) // Az új splitReq értéket beállítjuk
            // A sortPlayersMap() funkciót kihagyjuk, mivel az a Map mutálására irányul,
            // ami ellentmond az immutabilitásnak. (LinkedHashMap biztosítja a beszúrási sorrendet.)
            .build();

        // 10. Mentjük az új, teljes állapotot
        gameStateService.saveGameState(finalState);

        return finalState;
    }

    public GameState addToPlayersListByStand(Optional<GameState> gameStateOptional) {
        // 1. Állapot lekérése (a metódus teljes időtartamára változatlan marad)
        GameState oldState = gameStateManager.getGameStateOrThrow(gameStateOptional);
        PlayerHand playerHand = oldState.getPlayer();

        // Ellenőrző logika
        boolean isActive = gameStateManager.areStatedFalseHands(oldState.getPlayers());
        logger.info(">>>>y isActive: {}", isActive);

        if (isActive) {
            // --- IMMUTÁBILIS FRISSÍTÉSEK ---

            // 2. PlayerHand immutábilis frissítése: stated = true
            PlayerHand updatedPlayerHand = playerHand.toBuilder()
                .stated(true)
                .build();

            // 3. Players térkép immutábilis frissítése: új Map létrehozása
            // A Map-ek frissítését mindig klónozással kell kezdeni immutábilis környezetben!
            Map<String, PlayerHand> newPlayersMap = new HashMap<>(oldState.getPlayers());
            newPlayersMap.put(updatedPlayerHand.id(), updatedPlayerHand);

            // 4. PlayersIndex térkép immutábilis frissítése
            // Feltételezve, hogy a getPlayersIndex() Map<String, Boolean> típusú
            Map<String, Boolean> newPlayersIndexMap = new HashMap<>(oldState.getPlayersIndex());
            String ID = updatedPlayerHand.id();
            newPlayersIndexMap.put(ID, true);

            // 5. Végleges GameState objektum létrehozása az ÚJ referenciákkal
            GameState newState = oldState.toBuilder()
                // Frissített player objektumot használunk, mert ott van a stated(true)
                .player(updatedPlayerHand)

                // Új térkép referenciákat használunk
                .players(newPlayersMap)
                .playersIndex(newPlayersIndexMap)
                .build();

            gameStateService.saveGameState(newState);
            return newState;

        } else {
            // Ha nem aktív, nincs frissítés
            return oldState;
        }
    }

    public GameState addSplitPlayerToGame(Optional<GameState> gameStateOptional) {
        // 1. Állapot lekérése (ez az alap, amitől indulunk)
        GameState oldState = gameStateManager.getGameStateOrThrow(gameStateOptional);
        GameState currentGameState = oldState; // Ezt a referenciát láncoljuk!

        Map<String, Boolean> playersIndex = currentGameState.getPlayersIndex();
        Optional<String> nextIdOptional = gameStateManager.findNextActivePlayerId(playersIndex);

        if (nextIdOptional.isEmpty()) {
            return currentGameState; // Nincs mit tenni, visszatérünk a régivel
        }

        String nextHandId = nextIdOptional.get();
        PlayerHand player = currentGameState.getPlayer();
        PlayerHand splitPlayer = currentGameState.getSplitPlayer();

        // Ideiglenes frissített PlayerHand, amit be akarunk állítani:
        PlayerHand nextActiveHand = null;

        // ESET 1: VISSZATÖLTÉS (Cache Védelme)
        if (splitPlayer != null && splitPlayer.id() != null && splitPlayer.id().equals(nextHandId)) {
            // A 'player' Hand-et a splitPlayer-re állítjuk.
            nextActiveHand = splitPlayer;
        }
        // ESET 2: Gyors kiút (A lap már be van töltve, nincs változtatás a PlayerHand-en)
        else if (player != null && player.id().equals(nextHandId)) {
            nextActiveHand = player;
        }
        // ESET 3: ELSŐ FUTÁS (Lap kiemelése és splitReq csökkentése)
        else if (currentGameState.getPlayers().containsKey(nextHandId)) {

            // --- Players Map immutábilis kezelése (kiemelés) ---
            Map<String, PlayerHand> oldPlayersMap = currentGameState.getPlayers();

            // Másolat készítése és a Hand eltávolítása (immutábilisan)
            Map<String, PlayerHand> newPlayersMap = new HashMap<>(oldPlayersMap);
            PlayerHand nextHand = newPlayersMap.remove(nextHandId); // Mutábilis Map művelet a KLÓNON!

            nextActiveHand = nextHand;

            // --- GameState immutábilis frissítése a PlayerHand és Map változásokkal ---
            currentGameState = currentGameState.toBuilder()
                // Frissítjük a 'players' térképet az új, csökkentett térképpel
                .players(newPlayersMap)

                // Beállítjuk a splitPlayer-t az aktuális nextHand másolatával (toBuilder/build az immutabilitás miatt!)
                .splitPlayer(nextHand.toBuilder().build())

                // SplitReq immutábilis csökkentése. Feltételezve, hogy a 'splitReq' int!
                .splitReq(currentGameState.getSplitReq() - 1)
                .build();
        }

        // --- ÁLLAPOT FRISSÍTÉSE NEXT ACTIVE HAND-re ---
        // A Player mező frissítése a GameState-ben a kiválasztott Hand-re
        if (nextActiveHand != null) {
            currentGameState = currentGameState.toBuilder()
                .player(nextActiveHand)
                .build();
        }


        // 6. KÁRTYA OSZTÁS / HAND BŐVÍTÉS (Ha a Hand mérete < 2)
        PlayerHand playerToDealTo = currentGameState.getPlayer();

        if (playerToDealTo.hand().size() < 2) {

            // Állapotláncolás: A safeDealCard megváltoztatja a Deck állapotát!
            // A gameStateOptional már tartalmazza az aktuális currentGameState-et a Player frissítéssel
            DealResultWithState result = safeDealCard(Optional.of(currentGameState));
            Card cardToAdd = result.dealtCard();

            // A safeDealCard által létrehozott új GameState-re frissítjük az állapotláncot!
            currentGameState = result.newGameState();

            // --- PlayerHand lap hozzáadása (immutábilisan) ---
            LinkedList<Card> newHandList = new LinkedList<>(playerToDealTo.hand());
            newHandList.add(cardToAdd);

            boolean canSplit = gameRuleEngine.canSplit(newHandList);

            // Új PlayerHand objektum létrehozása az új kártyával és canSplit értékkel
            PlayerHand finalPlayerHand = playerToDealTo.toBuilder()
                .hand(newHandList)
                .canSplit(canSplit)
                .build();

            // Végleges GameState frissítése az új PlayerHand-del
            currentGameState = currentGameState.toBuilder()
                .player(finalPlayerHand)
                .build();
        }

        // 7. Végleges mentés és visszatérés
        gameStateService.saveGameState(currentGameState);
        return currentGameState;
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
        GameState oldState = gameStateManager.getGameStateOrThrow(gameStateOptional);
        GameState resetGameState = gameStateManager.resetRoundState(oldState);

        // 🌟 Immutábilis frissítés
        GameState finalState = resetGameState.toBuilder()
            .bet(0)
            .build();

        gameStateService.saveGameState(finalState);

        return finalState;
    }

    public record TransactionResult(GameState gameState, int transactionResult) {
        // A record automatikusan biztosítja a konstruktort és a gettereket.
    }
}
