package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.*;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.blackjack.blackjack.common.GameConstants.INITIAL_DECK_LENGTH;

@Service
public class GamePhaseService {
    public DeckPhaseResult calculateInitialPhase(Player player, GameState state) {
        PhaseState phase;
        int deckSize = (state.getDeck() != null) ? state.getDeck().getDeckLength() : INITIAL_DECK_LENGTH;

        /*if (player.getTokens() <= 0 && !state.isRoundActive()) {
            phase = PhaseState.OUT_OF_TOKENS;
        } else if (state.isRoundActive()) {
            phase = PhaseState.RECOVERY_DECISION;
        } else {
            phase = PhaseState.BETTING;
        }*/
        phase = PhaseState.BETTING;
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
            case SHUFFLING -> new PhaseResult(PhaseState.INIT_GAME, PhaseState.NONE, PhaseState.INIT_GAME);
            case INIT_GAME -> handleStartGameTransition(state);
            case MAIN_TURN -> handleHitTransition(state);

            default -> throw new IllegalStateException("Ismeretlen fázis: " + currState);
        };
    }

    private PhaseResult handleStartGameTransition(GameState state) {
        List<Integer> immediateStopStates = List.of(
            WinnerState.BLACKJACK_PLAYER_WON.getValue(),
            WinnerState.BLACKJACK_PUSH.getValue()
        );

        int nat21 = state.getNatural21();
        System.out.println(" >>>>>>>>>>> nat21: " + nat21);
        PhaseState pre = immediateStopStates.contains(nat21)
            ? PhaseState.MAIN_STAND_REWARDS_TRANSIT
            : PhaseState.MAIN_TURN;

        return new PhaseResult(PhaseState.INIT_GAME, pre, pre);
    }

    private PhaseResult handleHitTransition(GameState state) {
        assert state.getPlayer() != null;
        int playerHandSum = state.getPlayer().sum();
        System.out.println(" >>>>>>>>>>> playerHandSum: " + playerHandSum);
        PhaseState target = playerHandSum < 21
            ? PhaseState.MAIN_TURN
            : PhaseState.MAIN_STAND_REWARDS_TRANSIT;

        return new PhaseResult(target, PhaseState.NONE, target);
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
