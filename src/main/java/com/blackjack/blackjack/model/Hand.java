package com.blackjack.blackjack.model;

import java.util.List;

public interface Hand {
    List<Card> getHand();

    void setSum(int sum);
}
