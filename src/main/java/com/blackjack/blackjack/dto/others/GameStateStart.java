package com.blackjack.blackjack.dto.others;

import com.blackjack.blackjack.model.DealerHandMasked;
import com.blackjack.blackjack.model.PlayerHand;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Value
@Builder(toBuilder = true)
@AllArgsConstructor
public class GameStateStart {
    PlayerHand player;

    @JsonProperty("dealer_masked")
    DealerHandMasked dealerMasked;
    int deckLen;
    int bet;

    @JsonProperty("is_round_active")
    boolean isRoundActive;
}
