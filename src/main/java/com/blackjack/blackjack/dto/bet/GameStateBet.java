package com.blackjack.blackjack.dto.bet;

import com.blackjack.blackjack.model.PhaseState;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record GameStateBet(int bet, List<Integer> betList, int deckLen,
                           @JsonProperty("target_phase") PhaseState targetPhase,
                           @JsonProperty("pre_phase") PhaseState prePhase) {
}
