package com.blackjack.blackjack.dto.others;

import com.blackjack.blackjack.model.DealerHandUnmasked;
import com.blackjack.blackjack.model.PlayerHand;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.Map;

@Value
@Builder(toBuilder = true)
@AllArgsConstructor
public class GameStateAddPlayerFromPlayers {
    PlayerHand player;

    @JsonProperty("dealer_unmasked")
    DealerHandUnmasked dealerUnmasked;
    boolean aces;
    Map<String, PlayerHand> players;
    int splitReq;
    int deckLen;
    int bet;
    boolean isRoundActive;
}
