package com.blackjack.blackjack.dto.others;

import com.blackjack.blackjack.model.DealerHandUnmasked;
import com.blackjack.blackjack.model.PlayerHand;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedList;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameStateSplitStandAndRewards {
    private PlayerHand player;

    @JsonProperty("dealer_unmasked")
    private DealerHandUnmasked dealerUnmasked;
    private Map<String, PlayerHand> players;
    private int winner;
    private int splitReq;
    private int deckLen;
    private int bet;
    private boolean isRoundActive;
}
