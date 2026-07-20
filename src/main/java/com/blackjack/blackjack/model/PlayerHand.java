package com.blackjack.blackjack.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.io.Serializable;
import java.util.List;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlayerHand(String id, List<Card> hand, boolean canSplit, boolean stated,
                         int bet, int sum,
                         int handState, int hasHit) implements Hand, Serializable {
    public PlayerHand {
        hand = (hand == null) ? List.of() : List.copyOf(hand);
    }

    public static PlayerHand createEmptyHand() {
        return PlayerHand.builder()
            .id("NONE")
            .hand(List.of())
            .canSplit(false)
            .stated(false)
            .bet(0)
            .sum(0)
            .handState(0)
            .hasHit(0)
            .build();
    }
}
