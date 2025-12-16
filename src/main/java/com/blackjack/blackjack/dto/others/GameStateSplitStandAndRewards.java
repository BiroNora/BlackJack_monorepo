package com.blackjack.blackjack.dto.others;

import com.blackjack.blackjack.model.DealerHandUnmasked;
import com.blackjack.blackjack.model.PlayerHand;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.Map;

@Builder(toBuilder = true)
public record GameStateSplitStandAndRewards(PlayerHand player,
                                            @JsonProperty("dealer_unmasked") DealerHandUnmasked dealerUnmasked,
                                            Map<String, PlayerHand> players, int winner, int splitReq, int deckLen,
                                            int bet, boolean isRoundActive) {
}
