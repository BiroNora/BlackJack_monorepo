package com.blackjack.blackjack.dto.others;

import com.blackjack.blackjack.model.DealerHandMasked;
import com.blackjack.blackjack.model.PlayerHand;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameStateStart {
    private PlayerHand player;

    @JsonProperty("dealer_masked")
    private DealerHandMasked dealerMasked;
    private int deckLen;
    private int bet;

    @JsonProperty("is_round_active")
    private boolean isRoundActive;
}
