package com.blackjack.blackjack.service;

import com.blackjack.blackjack.dto.bet.GameStateBet;
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

    public GameState phaseOfSplitRequest(GameState state) {
        if (state.getPlayer() == null) {
            throw new IllegalStateException("No player hand.");
        }
        boolean isAces = state.isAces();
        boolean isHand21 = HandState.fromValue(state.getPlayer().handState()) == HandState.TWENTY_ONE;

        PhaseState target = isAces ? PhaseState.SPLIT_ACE_TRANSIT
            : isHand21 ? PhaseState.SPLIT_NAT21_TRANSIT
            : PhaseState.SPLIT_TURN;
        PhaseState pre = (isHand21 && !isAces) ? PhaseState.SPLIT_STAND : PhaseState.NONE;

        return state.toBuilder().targetPhase(target).prePhase(pre).build();
    }

    public GameState phaseOfAddToPlayersListByStand(GameState state) {
        boolean isSplitFinished = state.getSplitReq() == 0;

        PhaseState target = isSplitFinished ? PhaseState.SPLIT_FINISH : PhaseState.SPLIT_ACE_TRANSIT;

        return state.toBuilder().targetPhase(target).prePhase(PhaseState.NONE).build();
    }

    public GameState phaseOfAddSplitPlayerToGame(GameState state) {
        if (state.getPlayer() == null) {
            throw new IllegalStateException("Player hand is missing.");
        }
        boolean isAces = state.isAces();
        boolean isSplitFinished = state.getSplitReq() == 0;
        boolean isHand21 = HandState.fromValue(state.getPlayer().handState()) == HandState.TWENTY_ONE;

        PhaseState target = !isAces && isHand21 ? PhaseState.SPLIT_NAT21_TRANSIT
            : isAces && !isSplitFinished ? PhaseState.SPLIT_ACE_TRANSIT
            : isAces ? PhaseState.SPLIT_FINISH
            : PhaseState.SPLIT_TURN;

        PhaseState pre = isHand21 ? PhaseState.SPLIT_STAND : PhaseState.NONE;

        return state.toBuilder().targetPhase(target).prePhase(pre).build();
    }

    public GameState phaseOfAddPlayerFromPlayers(GameState state) {
        return state.toBuilder().targetPhase(PhaseState.SPLIT_FINISH).prePhase(PhaseState.NONE).build();
    }

    public GameStateBet phaseOfSetRestart(GameState state) {
        return GameStateBet.builder()
            .deckLen(INITIAL_DECK_LENGTH)
            .targetPhase(PhaseState.RESTART_GAME)
            .prePhase(PhaseState.NONE)
            .build();
    }

    /*public GameState phaseOfSetRestart(GameState state) {
        return state.toBuilder().targetPhase(PhaseState.RESTART_GAME).prePhase(PhaseState.NONE).build();
    }*/

    public interface PhaseResultProvider {
        PhaseState targetPhase();
        PhaseState prePhase();
    }

    public record DeckPhaseResult(
        int deckLen,
        PhaseState targetPhase,
        PhaseState prePhase
    ) implements PhaseResultProvider {
    }
}
