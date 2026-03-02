package com.blackjack.blackjack.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.io.Serializable;
import java.util.List;

@Builder(toBuilder = true)
public record DealerHandMasked(
    List<Card> hand,
    int sum,
    boolean canInsure
) implements Hand, Serializable {

    public DealerHandMasked {
        hand = (hand == null) ? List.of() : List.copyOf(hand);
    }

    public static DealerHandMasked createEmptyHand() {
        return DealerHandMasked.builder()
            .hand(List.of())
            .sum(0)
            .canInsure(false)
            .build();
    }
}
