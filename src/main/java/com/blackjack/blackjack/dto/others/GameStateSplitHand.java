package com.blackjack.blackjack.dto.others;

import com.blackjack.blackjack.model.DealerHandMasked;
import com.blackjack.blackjack.model.PlayerHand;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedList;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameStateSplitHand {
    private PlayerHand player;
    private DealerHandMasked dealerHandMasked;
    private boolean aces;
    private LinkedList<PlayerHand> players;
    private int splitReq;
    private int deckLen;
    private int bet;
    private boolean isRoundActive;
}
