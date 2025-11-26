package com.blackjack.blackjack.model;

import com.blackjack.blackjack.service.HandStateUpdater;
import com.blackjack.blackjack.service.HandValueCalculator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlayerHand implements Hand, Serializable {
    private String id;
    private LinkedList<Card> hand;
    private boolean canSplit;
    private boolean stated;
    private int bet;

    public static PlayerHand createEmptyHand() {
        PlayerHand hand = new PlayerHand();
        hand.setHand(new LinkedList<>());
        hand.setCanSplit(false);
        hand.setStated(false);
        hand.setBet(0);
        return hand;
    }

    public int getSum() {
        return HandValueCalculator.calculateSum(this.hand);
    }

    public int getHandState() {
        HandState state = HandStateUpdater.updateHandState(this.getSum(), this.hand.size());

        return state.getValue();
    }
}
