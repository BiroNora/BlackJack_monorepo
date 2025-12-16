package com.blackjack.blackjack.dto.bet;

import lombok.*;

import java.util.List;

@Value
@Builder(toBuilder = true)
@AllArgsConstructor
public class GameStateBet {
    int bet;
    List<Integer> betList;
    int deckLen;
}
