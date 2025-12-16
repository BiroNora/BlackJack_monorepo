package com.blackjack.blackjack.dto.others;

import com.blackjack.blackjack.model.PlayerHand;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Builder(toBuilder = true)
@AllArgsConstructor
public record GameStateDouble(PlayerHand player, int deckLen, boolean isRoundActive) {
}
