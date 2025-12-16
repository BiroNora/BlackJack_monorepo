package com.blackjack.blackjack.dto.others;

import com.blackjack.blackjack.model.DealerHandUnmasked;
import com.blackjack.blackjack.model.PlayerHand;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.Map;

@Builder(toBuilder = true)
public record GameStateAddPlayerFromPlayers(PlayerHand player,
                                            @JsonProperty("dealer_unmasked") DealerHandUnmasked dealerUnmasked,
                                            boolean aces, Map<String, PlayerHand> players, int splitReq, int deckLen,
                                            int bet, boolean isRoundActive) {
}
