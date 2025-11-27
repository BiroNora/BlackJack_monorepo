package com.blackjack.blackjack.dto.others;

import com.blackjack.blackjack.model.DealerHandUnmasked;
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
public class GameStateRewards {
    private PlayerHand player;

    @JsonProperty("dealer_unmasked")
    private DealerHandUnmasked dealerUnmasked;
    private int deckLen;
    private int bet;
    private int winner;
    private boolean isRoundActive;
}
