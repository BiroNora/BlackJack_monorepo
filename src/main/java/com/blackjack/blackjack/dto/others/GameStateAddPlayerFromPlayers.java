package com.blackjack.blackjack.dto.others;

import com.blackjack.blackjack.model.DealerHandMasked;
import com.blackjack.blackjack.model.DealerHandUnmasked;
import com.blackjack.blackjack.model.PlayerHand;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameStateAddPlayerFromPlayers {
    private PlayerHand player;

    @JsonProperty("dealer_unmasked")
    private DealerHandUnmasked dealerUnmasked;
    private boolean aces;
    private Map<String, PlayerHand> players;
    private int splitReq;
    private int deckLen;
    private int bet;
    private boolean isRoundActive;
}
