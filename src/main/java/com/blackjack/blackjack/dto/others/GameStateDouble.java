package com.blackjack.blackjack.dto.others;

import com.blackjack.blackjack.model.PlayerHand;
import lombok.Builder;

@Builder(toBuilder = true)
public record GameStateDouble(PlayerHand player, int deckLen, boolean isRoundActive) {
}
