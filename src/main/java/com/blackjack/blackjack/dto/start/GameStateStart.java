package com.blackjack.blackjack.dto.start;

import com.blackjack.blackjack.model.DealerHandMasked;
import com.blackjack.blackjack.model.PlayerHand;
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
    private DealerHandMasked dealerMasked;
    private int deckLen;
    private int bet;
    private boolean isRoundActive;
}
