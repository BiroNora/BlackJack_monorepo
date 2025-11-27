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

import java.util.LinkedList;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameStateAddToPlayersByStand {
    private PlayerHand player;
    private boolean aces;
    private Map<String, PlayerHand> players;
    private int splitReq;
    private int deckLen;
    private int bet;
    private boolean isRoundActive;

    private DealerHandMasked dealerMasked;
    private DealerHandUnmasked dealerUnmasked;

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
