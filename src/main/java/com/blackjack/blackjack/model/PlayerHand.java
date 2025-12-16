package com.blackjack.blackjack.model;

import com.blackjack.blackjack.service.HandStateUpdater;
import com.blackjack.blackjack.service.HandValueCalculator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

import java.io.Serializable;
import java.util.LinkedList;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Jacksonized
public record PlayerHand(String id, LinkedList<Card> hand, boolean canSplit, boolean stated,
                         int bet) implements Hand, Serializable {
    public static PlayerHand createEmptyHand() {
        return PlayerHand.builder()
            .id("NONE")
            .hand(new LinkedList<>())
            .canSplit(false)
            .stated(false)
            .bet(0)
            .build();
    }

    public int getSum() {
        return HandValueCalculator.calculateSum(this.hand);
    }

    public int getHandState() {
        HandState state = HandStateUpdater.updateHandState(this.getSum(), this.hand.size());

        return state.getValue();
    }
}
