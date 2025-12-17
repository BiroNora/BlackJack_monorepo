package com.blackjack.blackjack.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

import java.io.Serializable;
import java.util.List;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Jacksonized
public record DealerHandUnmasked(
    List<Card> hand,
    @JsonProperty("natural_21") int natural21, int sum,
    int handState
) implements Hand, Serializable {

    public DealerHandUnmasked {
        hand = (hand == null) ? List.of() : List.copyOf(hand);
    }

    public static DealerHandUnmasked createEmptyHand() {
        return DealerHandUnmasked.builder()
            .hand(List.of())
            .natural21(0)
            .sum(0)
            .handState(0)
            .build();
    }
}
