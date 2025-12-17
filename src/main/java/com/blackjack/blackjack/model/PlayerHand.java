package com.blackjack.blackjack.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

import java.io.Serializable;
import java.util.LinkedList;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Jacksonized
public record PlayerHand(String id, LinkedList<Card> hand, boolean canSplit, boolean stated,
                         int bet, int sum,
                         int handState) implements Hand, Serializable {
    public static PlayerHand createEmptyHand() {
        return PlayerHand.builder()
            .id("NONE")
            .hand(new LinkedList<>())
            .canSplit(false)
            .stated(false)
            .bet(0)
            .sum(0)
            .handState(0)
            .build();
    }
}
