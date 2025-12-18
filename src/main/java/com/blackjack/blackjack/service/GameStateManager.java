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
public class GameStateManager {
    private static final Logger logger = LoggerFactory.getLogger(GameStateManager.class);
    private static final Comparator<PlayerHand> SPLIT_HAND_COMPARATOR = Comparator
        .comparing(PlayerHand::stated)
        .thenComparing(PlayerHand::id);
    private final GameRuleEngine gameRuleEngine;
    private final PlayerHandFactory playerHandFactory;
    private final HandValueCalculator handValueCalculator;
    private final HandStateUpdater handStateUpdater;
    private final DeckService deckService;

    public GameStateManager(GameRuleEngine gameRuleEngine, PlayerHandFactory playerHandFactory, HandValueCalculator handValueCalculator, HandStateUpdater handStateUpdater, DeckService deckService) {
        this.gameRuleEngine = gameRuleEngine;
        this.playerHandFactory = playerHandFactory;
        this.handValueCalculator = handValueCalculator;
        this.handStateUpdater = handStateUpdater;
        this.deckService = deckService;
    }

    public GameState createNewDeck(GameState state) {
        Deck newDeck = deckService.createNewDeck();
        logger.info("+++++++++++ createNewDeck {}", newDeck);
        return state.toBuilder().deck(newDeck).build();
    }

    public GameState dealSplitCard(GameState state, boolean isFirst, Card cardToSplit) {
        GameState.GameStateBuilder newStateBuilder = state.toBuilder();
        PlayerHand updatedHand;
        String id;

        if (isFirst) {
            Deck currentDeck = state.getDeck();
            if (currentDeck == null || state.getDeckLen() == 0) {
                state = createNewDeck(state); // Automata újrakeverés ha kell
                newStateBuilder = state.toBuilder();
            }

            assert state.getDeck() != null;
            DealResult result = state.getDeck().dealCard();
            newStateBuilder.deck(result.newDeck());

            PlayerHand p = state.getPlayer();
            assert p != null;
            id = p.id();
            List<Card> newHandCards = new ArrayList<>(p.hand());
            newHandCards.add(result.dealtCard());

            int sum = handValueCalculator.calculateSum(newHandCards);
            HandState hState = handStateUpdater.updateHandState(sum, newHandCards.size());

            boolean canSplitAgain = gameRuleEngine.canSplit(newHandCards)
                && newHandCards.getFirst().rank() != Rank.ACE;

            updatedHand = p.toBuilder()
                .hand(Collections.unmodifiableList(newHandCards))
                .sum(sum)
                .handState(hState.getValue())
                .canSplit(canSplitAgain)
                .stated(false)
                .build();

            newStateBuilder.player(updatedHand);

        } else {
            int nextCount = state.calculateNextHandCounter();
            id = String.format("P-%03d", nextCount);

            assert state.getPlayer() != null;

            updatedHand = playerHandFactory.createEmptyHand().toBuilder()
                .id(id)
                .hand(List.of(cardToSplit))
                .bet(state.getPlayer().bet())
                .build();

            newStateBuilder.handCounter(nextCount);

            Map<String, PlayerHand> playersMap = new HashMap<>(state.getPlayers());
            playersMap.put(id, updatedHand);

            newStateBuilder.players(Collections.unmodifiableMap(playersMap));

        }

        Map<String, Boolean> playersIndex = new LinkedHashMap<>(state.getPlayersIndex());
        playersIndex.put(id, false);
        newStateBuilder.playersIndex(Collections.unmodifiableMap(playersIndex));
        logger.info("+++++++++++BBBBBB dealSplitCard playersIndex {}", playersIndex);
        return newStateBuilder.build();
    }
}
