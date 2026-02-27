package com.blackjack.blackjack.dto.others;

import com.blackjack.blackjack.model.PhaseState;
import com.blackjack.blackjack.model.PlayerHand;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder(toBuilder = true)
public record GameStateDouble(PlayerHand player, @JsonProperty("deck_len") int deckLen,
                              @JsonProperty("is_round_active") boolean isRoundActive,
                              @JsonProperty("target_phase") PhaseState targetPhase,
                              @JsonProperty("pre_phase") PhaseState prePhase) {
}
