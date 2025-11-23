package com.blackjack.blackjack.dto.others;

import com.blackjack.blackjack.model.PlayerHand;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameStateDouble {
    private PlayerHand player;
    private int deckLen;
    private boolean isRoundActive;
}
