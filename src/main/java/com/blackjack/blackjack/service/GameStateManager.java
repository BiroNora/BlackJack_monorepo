package com.blackjack.blackjack.service;

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

    public GameStateManager(GameRuleEngine gameRuleEngine) {
        this.gameRuleEngine = gameRuleEngine;
    }

    public GameState getGameStateOrThrow(Optional<GameState> gameStateOptional) {
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
        gameState.getPlayers().clear();

        return gameState;
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
            boolean isSplitPossible = gameRuleEngine.canSplit(currentHand);
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
