package com.blackjack.blackjack.dto.others;

import com.blackjack.blackjack.model.DealerHandMasked;
import com.blackjack.blackjack.model.PhaseState;
import com.blackjack.blackjack.model.PlayerHand;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder(toBuilder = true)
public record GameStateStart(PlayerHand player, @JsonProperty("dealer_masked") DealerHandMasked dealerMasked,
                             @JsonProperty("deck_len") int deckLen, int bet,
                             @JsonProperty("target_phase") PhaseState targetPhase,
                             @JsonProperty("pre_phase") PhaseState prePhase) {
}
