package com.blackjack.blackjack.dto.others;

import com.blackjack.blackjack.model.DealerHandMasked;
import com.blackjack.blackjack.model.DealerHandUnmasked;
import com.blackjack.blackjack.model.PlayerHand;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder(toBuilder = true)
public record GameStateInsurance(PlayerHand player, @JsonProperty("natural_21") int natural21, int deckLen, int bet,
                                 boolean isRoundActive, DealerHandMasked dealerMasked,
                                 DealerHandUnmasked dealerUnmasked) {
    @JsonProperty("dealer_unmasked")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public DealerHandUnmasked getDealerUnmaskedForInsurance() {
        if (this.natural21 == 3) {
            return this.dealerUnmasked;
        }
        return null; // Ha null, a Jackson kihagyja a mezőt
    }

    @JsonProperty("dealer_masked")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public DealerHandMasked getDealerMaskedForInsurance() {
        if (this.natural21 != 3) {
            return this.dealerMasked;
        }
        return null; // Ha null, a Jackson kihagyja a mezőt
    }
}
