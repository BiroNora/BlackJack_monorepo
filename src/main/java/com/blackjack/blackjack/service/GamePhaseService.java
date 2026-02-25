package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.PhaseState;
import com.blackjack.blackjack.model.Player;

import static com.blackjack.blackjack.common.GameConstants.INITIAL_DECK_LENGTH;

public class GamePhaseService {
    public DeckPhaseResult calculateInitialPhase(Player player, GameState state) {
        PhaseState phase;
        int deckSize = (state.getDeck() != null) ? state.getDeck().getDeckLength() : INITIAL_DECK_LENGTH;

        if (player.getTokens() <= 0 && !state.isRoundActive()) {
            phase = PhaseState.OUT_OF_TOKENS;
        } else if (state.isRoundActive()) {
            phase = PhaseState.RECOVERY_DECISION;
        } else {
            phase = PhaseState.BETTING;
        }
        return new DeckPhaseResult(deckSize, phase, phase);
    }

    public DeckPhaseResult calculateDeckAndPhase(GameState state) {
        int deckSize = (!state.isRoundActive() && state.isSessionInit()) || state.getDeck() == null
            ? INITIAL_DECK_LENGTH
            : state.getDeck().getDeckLength();

        PhaseState phase = (deckSize == INITIAL_DECK_LENGTH || deckSize < 60)
            ? PhaseState.SHUFFLING
            : PhaseState.INIT_GAME;

        return new DeckPhaseResult(deckSize, PhaseState.BETTING, phase);
    }

    public interface PhaseResultProvider {
        PhaseState currentPhase();
    }

    public record PhaseResult(PhaseState targetPhase, PhaseState currentPhase)
        implements PhaseResultProvider {
    }

    public record PrePhaseResult(PhaseState targetPhase, PhaseState prePhase, PhaseState currentPhase)
        implements PhaseResultProvider {
    }

    public record DeckPhaseResult(int deckLen, PhaseState targetPhase, PhaseState currentPhase) {
    }
}
