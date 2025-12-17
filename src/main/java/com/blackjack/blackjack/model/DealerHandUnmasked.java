package com.blackjack.blackjack.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DealerHandUnmasked(
    List<Card> hand,
    @JsonProperty("natural_21") int natural21, int sum,
    int handState
) implements Hand, Serializable {

    public static DealerHandUnmasked createEmptyHand() {
        return DealerHandUnmasked.builder()
            .hand(new ArrayList<>())
            .natural21(0)
            .sum(0)
            .handState(0)
            .build();
    }
}
