package com.blackjack.blackjack.dto.bet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameStateBet {
    private int bet;
    private List<Integer> betList;
    private int deckLen;
}
