package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.HandState;
import com.blackjack.blackjack.model.PhaseState;
import com.blackjack.blackjack.model.Player;
import org.springframework.stereotype.Service;

import static com.blackjack.blackjack.common.GameConstants.INITIAL_DECK_LENGTH;

@Service
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

        PhaseState prePhase = (deckSize == INITIAL_DECK_LENGTH || deckSize < 60)
            ? PhaseState.SHUFFLING
            : PhaseState.INIT_GAME;

        return new DeckPhaseResult(deckSize, PhaseState.BETTING, prePhase);
    }

    public PhaseResult calculateNextPhase(GameState state) {
        PhaseState currState = state.getCurrentPhase();
        System.out.println("currState: " + currState);

        return switch (currState) {
            case SHUFFLING -> new PhaseResult(PhaseState.INIT_GAME, PhaseState.NONE, currState);

            default -> throw new IllegalStateException("Ismeretlen fázis: " + currState);
        };
    }

    // A kiszervezett split logika
    private PhaseResult handleSplitLogic(GameState state) {
        boolean hasAces = state.isAces();
        int splitReq = state.getSplitReq();
        assert state.getPlayer() != null;
        HandState playerHandState = HandState.fromValue(state.getPlayer().handState());

        boolean isFinished = playerHandState == HandState.TWENTY_ONE ||
            playerHandState == HandState.BLACKJACK;

        PhaseState target = !hasAces && isFinished ? PhaseState.SPLIT_NAT21_TRANSIT :
            hasAces && splitReq != 0 ? PhaseState.SPLIT_ACE_TRANSIT :
                hasAces ? PhaseState.SPLIT_FINISH :
                    PhaseState.SPLIT_TURN;

        PhaseState pre = isFinished ? PhaseState.SPLIT_STAND : PhaseState.NONE;

        return new PhaseResult(target, pre, PhaseState.SPLIT_TURN);
    }


    public interface PhaseResultProvider {
        PhaseState currentPhase();
    }

    public record PhaseResult(PhaseState targetPhase, PhaseState prePhase, PhaseState currentPhase)
        implements PhaseResultProvider {
    }

    public record DeckPhaseResult(int deckLen, PhaseState targetPhase, PhaseState currentPhase)
        implements PhaseResultProvider {
    }
}
