package com.blackjack.blackjack.service;

import com.blackjack.blackjack.factory.PlayerHandFactory;
import com.blackjack.blackjack.model.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class GameStateManager {
    private static final Comparator<PlayerHand> SPLIT_HAND_COMPARATOR = Comparator
        .comparing(PlayerHand::isStated)
        .thenComparing(PlayerHand::getId);
    private final GameRuleEngine gameRuleEngine;
    private final PlayerHandFactory playerHandFactory;

    public GameStateManager(GameRuleEngine gameRuleEngine, PlayerHandFactory playerHandFactory) {
        this.gameRuleEngine = gameRuleEngine;
        this.playerHandFactory = playerHandFactory;
    }

    public GameState getGameStateOrThrow(Optional<GameState> gameStateOptional) {
        return gameStateOptional.orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "CLIENT_ERROR_SPECIFIC"
        ));
    }

    public GameState resetRoundState(GameState oldState) {
        PlayerHand resetPlayerHand = PlayerHand.createEmptyHand();
        PlayerHand resetSplitPlayer = PlayerHand.createEmptyHand();

        DealerHandMasked resetDealerMasked = DealerHandMasked.createEmptyHand();
        DealerHandUnmasked resetDealerUnmasked = DealerHandUnmasked.createEmptyHand();

        GameState newState = oldState.toBuilder()
            .betList(List.of())
            .player(resetPlayerHand)
            .dealerMasked(resetDealerMasked)
            .dealerUnmasked(resetDealerUnmasked)
            .splitPlayer(resetSplitPlayer)
            .aces(false)
            .natural21(0)
            .winner(0)
            .handCounter(0)
            .splitReq(0)
            .isRoundActive(false)
            .players(new HashMap<>())
            .build();

        return newState;
    }

    // A metódus most a frissített GameState-et adja vissza.
// A hívó fél (pl. GameService) felelőssége lesz menteni.
    public GameState dealSplitCard(GameState oldState, boolean isFirst, Card cardToSplit) {

        // Kezdeti állapotváltozók beállítása a builder segítségével
        GameState.GameStateBuilder newStateBuilder = oldState.toBuilder();

        // Ideiglenes változók
        PlayerHand updatedPlayerHand;
        Deck newDeckState = oldState.getDeck(); // Alapértelmezett állapot, ha nincs laposztás

        if (isFirst) {
            // --- Első osztás (lapot húzunk a pakliból) ---

            Deck currentDeck = oldState.getDeck();

            // 🚨 Változás 1: IMMUTABLE laposztás
            DealResult result = currentDeck.dealCard();
            Card dealtCard = result.getDealtCard();
            newDeckState = result.getNewDeck(); // 🚨 Új pakli állapot

            // Frissítjük a Deck állapotot a Builderben
            newStateBuilder.deck(newDeckState);

            // Régi kéz betöltése
            PlayerHand oldPlayerHand = oldState.getPlayer();

            // Létrehozzuk az új kéz állapotot (PlayerHand.toBuilder() is kell)
            // 🚨 Feltételezés: A PlayerHand is @Builder(toBuilder=true)

            // 1. Készítünk egy módosítható másolatot a kártyákról
            LinkedList<Card> currentHand = new LinkedList<>(oldPlayerHand.getHand());
            currentHand.add(dealtCard); // Hozzáadjuk a húzott lapot

            // 2. Kiszámoljuk a split-elhetőséget (ideális esetben ez a GameRuleEngine-ben történik)
            boolean isSplitPossible = gameRuleEngine.canSplit(currentHand);
            boolean finalCanSplit;

            if (currentHand.getFirst().rank() == Rank.ACE && currentHand.getLast().rank() == Rank.ACE) {
                finalCanSplit = false;
            } else {
                finalCanSplit = isSplitPossible;
            }

            // Létrehozzuk az UPDATED PlayerHand objektumot
            updatedPlayerHand = oldPlayerHand.toBuilder()
                .hand(currentHand)
                .canSplit(finalCanSplit) // Beállítjuk az új értéket
                .bet(oldState.getBet())
                .stated(false) // Beállítjuk az új értéket
                .build();

            // Frissítjük a GameState-et az UPDATED PlayerHand-del
            newStateBuilder.player(updatedPlayerHand);

        } else {
            // --- További osztások (új, split-elt kéz létrehozása) ---

            // A PlayerHandFactory-nak is Builder-t kellene használnia.
            PlayerHand newSplitHand = playerHandFactory.createEmptyHand();

            // 🚨 Változás 2: Az állapotmódosítást a Builderben végezzük
            String id = oldState.generateNextHandId(); // Feltételezzük, hogy ez a metódus már a builder mintához igazodik

            // Létrehozzuk az új Split Hand objektumot
            updatedPlayerHand = newSplitHand.toBuilder()
                .id(id) // Új ID beállítása
                .hand(new LinkedList<>(List.of(cardToSplit))) // Új kéz, a split lapokkal
                .bet(oldState.getBet()) // Az eredeti tét beállítása
                .build();

            // 🚨 Változás 3: A splitPlayer mező frissítése a GameState-ben
            newStateBuilder.splitPlayer(updatedPlayerHand);
        }

        // 4. Állapotmódosítás (Mindkét ág után fut)

        // A playersIndex módosítása:
        // 🚨 A Map is immutable módon frissüljön!
        Map<String, Boolean> newPlayersIndex = new LinkedHashMap<>(oldState.getPlayersIndex());
        newPlayersIndex.put(updatedPlayerHand.getId(), false);

        // Frissítjük a GameState Builder-t a módosított Map-pel
        GameState finalState = newStateBuilder
            .playersIndex(newPlayersIndex)
            .build(); // 👈 Létrejön az új, végső GameState objektum

        // A hívónak kell majd mentenie a finalState-et.
        return finalState;
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

    public PlayerHand deepCopyPlayerHand(PlayerHand original) {
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
}
