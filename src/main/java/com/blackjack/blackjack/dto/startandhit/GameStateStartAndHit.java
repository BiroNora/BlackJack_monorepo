package com.blackjack.blackjack.dto.startandhit;

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
public class GameStateStartAndHit {
    private PlayerHand playerHand;
    private DealerHandMasked dealerHandMasked;
    private int deckLen;
    private int bet;
    private boolean isRoundActive;
}
