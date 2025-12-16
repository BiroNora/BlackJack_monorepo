package com.blackjack.blackjack.dto.others;

import com.blackjack.blackjack.model.DealerHandUnmasked;
import com.blackjack.blackjack.model.PlayerHand;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Value
@Builder(toBuilder = true)
@AllArgsConstructor
public class GameStateRewards {
    PlayerHand player;

    @JsonProperty("dealer_unmasked")
    DealerHandUnmasked dealerUnmasked;
    int deckLen;
    int bet;
    int winner;
    boolean isRoundActive;
}
