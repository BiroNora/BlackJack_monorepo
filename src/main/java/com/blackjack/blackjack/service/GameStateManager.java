package com.blackjack.blackjack.service;

import com.blackjack.blackjack.factory.PlayerHandFactory;
import com.blackjack.blackjack.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GameStateManager {
    private static final Logger logger = LoggerFactory.getLogger(GameStateManager.class);
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

    public GameState resetRoundState(GameState oldState) {
        PlayerHand resetPlayerHand = PlayerHand.createEmptyHand();
        PlayerHand resetSplitPlayer = PlayerHand.createEmptyHand();

        DealerHandMasked resetDealerMasked = DealerHandMasked.createEmptyHand();
        DealerHandUnmasked resetDealerUnmasked = DealerHandUnmasked.createEmptyHand();

        return oldState.toBuilder()
            .betList(List.of())
            .player(resetPlayerHand)
            .dealerMasked(resetDealerMasked)
            .dealerUnmasked(resetDealerUnmasked)
            .splitPlayer(resetSplitPlayer)
            .aces(false)
            .natural21(0)
            .winner(0)
            .handCounter(0)
            .wasSplitInRound(false)
            .splitReq(0)
            .isRoundActive(false)
            .players(new HashMap<>())
            .build();
    }

    public GameState createNewDeck(GameState state) {
        Deck newDeck = deckService.createNewDeck();
        logger.info("+++++++++++ createNewDeck {}", newDeck);
        return state.toBuilder().deck(newDeck).build();
    }

    public GameService.DealResultWithState safeDealCard(GameState state) {
        GameState workingState = (state.getDeck() == null || state.getDeckLen() == 0)
            ? createNewDeck(state)
            : state;

        try {
            DealResult result = deckService.dealCard(workingState.getDeck());

            GameState newState = workingState.toBuilder()
                .deck(result.newDeck())
                .build();

            return new GameService.DealResultWithState(result.dealtCard(), newState);

        } catch (IllegalStateException e) {
            logger.warn("!!! DECK EXHAUSTED: Reshuffling automatically.");

            GameState reshuffledState = createNewDeck(workingState);
            DealResult result = deckService.dealCard(reshuffledState.getDeck());

            GameState finalState = reshuffledState.toBuilder()
                .deck(result.newDeck())
                .build();

            return new GameService.DealResultWithState(result.dealtCard(), finalState);
        }
    }

    public GameState processDealerDrawing(GameState currentState) {
        while (true) {
            assert currentState.getDealerUnmasked() != null;
            if (!(handValueCalculator.calculateSum(currentState.getDealerUnmasked().hand()) < 17)) break;

            GameService.DealResultWithState dealResult = safeDealCard(currentState);

            Card newCard = dealResult.dealtCard();
            GameState deckUpdatedState = dealResult.newGameState();

            DealerHandUnmasked oldDealerHand = deckUpdatedState.getDealerUnmasked();

            assert oldDealerHand != null;
            LinkedList<Card> newDealerHandList = new LinkedList<>(oldDealerHand.hand());
            newDealerHandList.add(newCard);

            int newDealerSum = handValueCalculator.calculateSum(newDealerHandList);
            HandState newDealerHandState = handStateUpdater.updateHandState(newDealerSum, newDealerHandList.size(), false);

            DealerHandUnmasked newDealerHand = oldDealerHand.toBuilder()
                .hand(newDealerHandList)
                .sum(newDealerSum)
                .handState(newDealerHandState.getValue())
                .build();

            currentState = deckUpdatedState.toBuilder()
                .dealerUnmasked(newDealerHand)
                .build();

            if (newDealerSum > 21) {
                break;
            }
        }
        return currentState;
    }

    public GameState dealSplitCard(GameState state, boolean isFirst, Card cardToSplit) {
        GameState.GameStateBuilder newStateBuilder = state.toBuilder();
        PlayerHand updatedHand;
        String id;

        if (isFirst) {
            GameState workingState = (state.getDeck() == null || state.getDeckLen() == 0)
                ? createNewDeck(state)
                : state;

            newStateBuilder = workingState.toBuilder();

            assert state.getDeck() != null;
            DealResult result = deckService.dealCard(workingState.getDeck());
            newStateBuilder.deck(result.newDeck());

            PlayerHand p = state.getPlayer();
            assert p != null;
            id = p.id();
            List<Card> newHandCards = new ArrayList<>(p.hand());
            newHandCards.add(result.dealtCard());

            int sum = handValueCalculator.calculateSum(newHandCards);
            HandState hState = handStateUpdater.updateHandState(sum, newHandCards.size(), true);

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
        //logger.info("+++++++++++BBBBBB dealSplitCard playersIndex {}", playersIndex);
        return newStateBuilder.build();
    }

    public Optional<String> findNextActivePlayerId(Map<String, Boolean> playersIndex) {
        //logger.info(" XXXXXXXX VVVVV playersIndex {}).", playersIndex);
        if (playersIndex == null || playersIndex.isEmpty()) {
            return Optional.empty();
        }

        return playersIndex.entrySet().stream()
            .filter(entry -> !entry.getValue())
            .map(Map.Entry::getKey)
            .sorted()
            .findFirst();
    }

    public boolean areStatedFalseHands(Map<String, PlayerHand> playersMap) {
        if (playersMap == null || playersMap.isEmpty()) {
            return false;
        }
        return playersMap.values().stream()
            .anyMatch(hand -> !hand.stated());
    }
}
