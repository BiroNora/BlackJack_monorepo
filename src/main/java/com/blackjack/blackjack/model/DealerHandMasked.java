package com.blackjack.blackjack.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Builder(toBuilder = true)
public record DealerHandMasked(
    List<Card> hand,
    int sum,
    boolean canInsure,
    @JsonProperty("nat_21") int nat21
) implements Hand, Serializable {

    public static DealerHandMasked createEmptyHand() {
        return DealerHandMasked.builder()
            .hand(new ArrayList<>())
            .sum(0)
            .canInsure(false)
            .nat21(0)
            .build();
    }
}
