package com.blackjack.blackjack.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.io.Serializable;
import java.util.List;

@Builder(toBuilder = true)
public record DealerHandMasked(
    List<Card> hand,
    boolean canInsure
) implements Hand, Serializable {

    public DealerHandMasked {
        hand = (hand == null) ? List.of() : List.copyOf(hand);
    }

    public static DealerHandMasked createEmptyHand() {
        return DealerHandMasked.builder()
            .hand(List.of())
            .canInsure(false)
            .build();
    }
}
