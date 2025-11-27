package com.blackjack.blackjack.dto.others;

import com.blackjack.blackjack.model.DealerHandMasked;
import com.blackjack.blackjack.model.DealerHandUnmasked;
import com.blackjack.blackjack.model.PlayerHand;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameStateInsurance {
    private PlayerHand player;

    @JsonProperty("natural_21")
    private int natural21;
    private int deckLen;
    private int bet;
    private boolean isRoundActive;

    private DealerHandMasked dealerMasked;
    private DealerHandUnmasked dealerUnmasked;

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
