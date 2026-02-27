package com.blackjack.blackjack.dto.others;

import com.blackjack.blackjack.model.DealerHandUnmasked;
import com.blackjack.blackjack.model.PhaseState;
import com.blackjack.blackjack.model.PlayerHand;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.Map;

@Builder(toBuilder = true)
public record GameStateSplitStandAndRewards(PlayerHand player,
                                            @JsonProperty("dealer_unmasked") DealerHandUnmasked dealerUnmasked,
                                            Map<String, PlayerHand> players, int winner,
                                            @JsonProperty("split_req") int splitReq,
                                            @JsonProperty("deck_len") int deckLen,
                                            int bet, @JsonProperty("is_round_active") boolean isRoundActive,
                                            @JsonProperty("target_phase") PhaseState targetPhase,
                                            @JsonProperty("pre_phase") PhaseState prePhase) {
}
