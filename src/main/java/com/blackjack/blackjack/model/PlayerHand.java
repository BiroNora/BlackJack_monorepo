package com.blackjack.blackjack.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

@Data
public class PlayerHand implements Serializable {
    private String id;
    private List<Card> hand;
    private int sum;
    private HandState handState;
    private boolean canSplit;
    private boolean stated;
    private int bet;

    public static PlayerHand createEmptyHand() {
        PlayerHand hand = new PlayerHand();
        hand.setHand(Collections.emptyList());
        hand.setSum(0);
        hand.setHandState(HandState.NONE);
        hand.setCanSplit(false);
        hand.setStated(false);
        hand.setBet(0);
        return hand;
    }

    public void setBet(int betAmount) {
        this.bet = betAmount;
    }
}
