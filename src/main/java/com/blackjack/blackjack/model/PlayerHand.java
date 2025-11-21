package com.blackjack.blackjack.model;

import com.blackjack.blackjack.service.HandStateUpdater;
import com.blackjack.blackjack.service.HandValueCalculator;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class PlayerHand implements Hand, Serializable {
    private String id;
    private List<Card> hand;
    private HandState handState;
    private boolean canSplit;
    private boolean stated;
    private int bet;

    public static PlayerHand createEmptyHand() {
        PlayerHand hand = new PlayerHand();
        hand.setHand(new ArrayList<>());
        hand.setHandState(HandState.NONE);
        hand.setCanSplit(false);
        hand.setStated(false);
        hand.setBet(0);
        return hand;
    }

    public int getSum() {
        return HandValueCalculator.calculateSum(this.hand);
    }

    public HandState getHandState() {
        return HandStateUpdater.updateHandState(this.getSum(), this.hand.size());
    }
}
