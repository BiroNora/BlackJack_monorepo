package com.blackjack.blackjack.model;

import com.blackjack.blackjack.service.HandStateUpdater;
import com.blackjack.blackjack.service.HandValueCalculator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.io.Serializable;
import java.util.LinkedList;

@Value
@Builder(toBuilder = true)
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Jacksonized
public class PlayerHand implements Hand, Serializable {
    String id;
    LinkedList<Card> hand;
    boolean canSplit;
    boolean stated;
    int bet;

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
