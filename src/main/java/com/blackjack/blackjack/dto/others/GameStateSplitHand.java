package com.blackjack.blackjack.dto.others;

import com.blackjack.blackjack.model.DealerHandMasked;
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
public class GameStateSplitHand {
    private PlayerHand player;

    @JsonProperty("dealer_masked")
    private DealerHandMasked dealerHandMasked;
    private boolean aces;
    private Map<String, PlayerHand> players;
    private int splitReq;
    private int deckLen;
    private int bet;
    private boolean isRoundActive;
}
