package com.blackjack.blackjack.dto.others;

import com.blackjack.blackjack.model.DealerHandMasked;
import com.blackjack.blackjack.model.DealerHandUnmasked;
import com.blackjack.blackjack.model.PlayerHand;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.Map;

@Builder(toBuilder = true)
public record GameStateAddToPlayersByStand(boolean aces, PlayerHand player, Map<String, PlayerHand> players,
                                           int splitReq, int deckLen, int bet, boolean isRoundActive,
                                           DealerHandMasked dealerMasked, DealerHandUnmasked dealerUnmasked) {
    @JsonProperty("dealer_masked")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public DealerHandMasked getDealerMaskedForAddToPlayersByStand() {
        if (this.splitReq > 0) {
            return this.dealerMasked;
        }
        return null; // Ha null, a Jackson kihagyja a mezőt
    }

    @JsonProperty("dealer_unmasked")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public DealerHandUnmasked getDealerUnmaskedForAddToPlayersByStand() {
        if (this.splitReq <= 0) {
            return this.dealerUnmasked;
        }
        return null; // Ha null, a Jackson kihagyja a mezőt
    }


}
