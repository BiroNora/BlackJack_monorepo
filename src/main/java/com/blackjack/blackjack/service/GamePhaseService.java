package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.PhaseState;
import com.blackjack.blackjack.model.Player;

import static com.blackjack.blackjack.common.GameConstants.INITIAL_DECK_LENGTH;

public class GamePhaseService {
    public PhaseState calculateInitialPhase(Player player, GameState state) {
        if (player.getTokens() <= 0 && !state.isRoundActive()) {
            return PhaseState.OUT_OF_TOKENS;
        } else if (state.isRoundActive()) {
            return PhaseState.RECOVERY_DECISION;
        } else {
            return PhaseState.BETTING;
        }
    }

    public DeckPhaseResult calculateDeckAndPhase(GameState state) {
        int deckSize = (!state.isRoundActive() && state.isSessionInit()) || state.getDeck() == null
            ? INITIAL_DECK_LENGTH
            : state.getDeck().getDeckLength();

        PhaseState phase = (deckSize == INITIAL_DECK_LENGTH || deckSize < 60)
            ? PhaseState.SHUFFLING
            : PhaseState.INIT_GAME;

        return new DeckPhaseResult(deckSize, phase);
    }


    public record DeckPhaseResult(int deckLen, PhaseState targetPhase) {}
}
