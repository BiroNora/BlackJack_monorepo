package com.blackjack.blackjack.dto.bet;

import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
@AllArgsConstructor
public record GameStateBet(int bet, List<Integer> betList, int deckLen) {
}
