package com.blackjack.blackjack.dto.others;

import com.blackjack.blackjack.model.DealerHandUnmasked;
import com.blackjack.blackjack.model.PlayerHand;
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
    private DealerHandUnmasked dealerUnmasked;
    private int deckLen;
    private int bet;
    private int winner;
    private boolean isRoundActive;
}
