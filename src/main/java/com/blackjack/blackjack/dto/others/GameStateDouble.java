package com.blackjack.blackjack.dto.others;

import com.blackjack.blackjack.model.PlayerHand;
import lombok.*;

@Value
@Builder(toBuilder = true)
@AllArgsConstructor
public class GameStateDouble {
    PlayerHand player;
    int deckLen;
    boolean isRoundActive;
}
