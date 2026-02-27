package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.*;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.blackjack.blackjack.common.GameConstants.INITIAL_DECK_LENGTH;

@Service

public class GamePhaseService {
    public DeckPhaseResult phaseOfInitialSession(Player player, GameState state) {
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

    public PhaseState updatePrePhaseBasedOnTokens(GameState state, int currentTokens) {
        PhaseState pre;
        if (currentTokens <= 0 && state.getPlayers().isEmpty()) {
            pre = PhaseState.OUT_OF_TOKENS;
        } else {
            pre = PhaseState.BETTING;
        }

        return pre;
    }

    public DeckPhaseResult phaseOfBetAndRetakeBet(GameState state) {
        int deckSize = (!state.isRoundActive() && state.isSessionInit()) || state.getDeck() == null
            ? INITIAL_DECK_LENGTH
            : state.getDeck().getDeckLength();

        PhaseState nextStep = (deckSize == INITIAL_DECK_LENGTH || deckSize < 60)
            ? PhaseState.SHUFFLING
            : PhaseState.INIT_GAME;

        return new DeckPhaseResult(
            deckSize,
            PhaseState.BETTING,
            nextStep
        );
    }

    public GameState phaseOfCreateDeck(GameState state) {
        return state.toBuilder().targetPhase(PhaseState.INIT_GAME).prePhase(PhaseState.NONE).build();
    }

    public GameState phaseOfStartGame(GameState state) {
        List<Integer> immediateStopStates = List.of(
            WinnerState.BLACKJACK_PLAYER_WON.getValue(),
            WinnerState.BLACKJACK_PUSH.getValue()
        );

        int nat21 = state.getNatural21();
        System.out.println(" >>>>>>>>>>> nat21: " + nat21);
        PhaseState pre = immediateStopStates.contains(nat21)
            ? PhaseState.MAIN_STAND_REWARDS_TRANSIT
            : PhaseState.MAIN_TURN;

        return state.toBuilder().targetPhase(PhaseState.INIT_GAME).prePhase(pre).build();
    }

    public GameState phaseOfHit(GameState state, boolean isDouble) {
        if (state.getPlayer() == null) {
            throw new IllegalStateException("No player hand.");
        }

        PhaseState target;
        int playerHandSum = state.getPlayer().sum();
        int hitCount = state.getPlayer().hasHit();
        boolean isSplitted = state.isHasSplit();

        if (!isSplitted) {
            target = (playerHandSum >= 21 || isDouble)
                ? PhaseState.MAIN_STAND_REWARDS_TRANSIT
                : PhaseState.MAIN_TURN;
        } else {
            if (isDouble) {
                target = PhaseState.SPLIT_STAND_DOUBLE;
            } else if (playerHandSum >= 21) {
                target = (hitCount == 1)
                    ? PhaseState.SPLIT_STAND_DOUBLE
                    : PhaseState.SPLIT_STAND;
            } else {
                target = PhaseState.SPLIT_TURN;
            }
        }

        return state.toBuilder().targetPhase(target).prePhase(PhaseState.NONE).build();
    }

    public GameState phaseOfStand(GameState state, int finalTokens) {
        PhaseState target;
        PhaseState pre;
        boolean isSplitted = state.isHasSplit();

        target = (isSplitted)
            ? PhaseState.SPLIT_FINISH_OUTCOME
            : PhaseState.MAIN_STAND;

        pre = updatePrePhaseBasedOnTokens(state, finalTokens);

        return state.toBuilder().targetPhase(target).prePhase(pre).build();
    }

    public GameState phaseOfInsRequest(GameState state) {
        if (state.getDealerUnmasked() == null) {
            throw new IllegalStateException("No dealer hand.");
        }
        PhaseState target;
        PhaseState pre;
        boolean dealerBJ = state.getDealerUnmasked().natural21() == 3;

        if (dealerBJ) {
            target = PhaseState.MAIN_STAND;
            pre = PhaseState.BETTING;
        } else {
            target = PhaseState.MAIN_TURN;
            pre = PhaseState.NONE;
        }

        return state.toBuilder().targetPhase(target).prePhase(pre).build();
    }


    private PhaseResult handleStandAndRewards(GameState state) {
        PhaseState target = (state.isHasSplit())
            ? PhaseState.SPLIT_FINISH_OUTCOME
            : PhaseState.MAIN_STAND;

        return new PhaseResult(target, PhaseState.BETTING);

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

        return new PhaseResult(target, pre);
    }


    public interface PhaseResultProvider {
        PhaseState targetPhase();

        PhaseState prePhase();
    }

    public record PhaseResult(
        PhaseState targetPhase,
        PhaseState prePhase)
        implements PhaseResultProvider {
    }

    public record DeckPhaseResult(
        int deckLen,
        PhaseState targetPhase,
        PhaseState prePhase
    ) implements PhaseResultProvider {
    }
}
