package com.blackjack.blackjack.dto.bet;

import com.blackjack.blackjack.model.PhaseState;
import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record GameStateBet(int bet, List<Integer> betList, int deckLen, PhaseState targetPhase) {
}
