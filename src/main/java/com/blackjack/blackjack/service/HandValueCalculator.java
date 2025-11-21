package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.Card;
import com.blackjack.blackjack.model.Rank;

import java.util.List;

public class HandValueCalculator {
    public static int calculateSum(List<Card> hand) {
        if (hand == null || hand.isEmpty()) {
            return 0;
        }

        int currentSum = 0;
        int aceCount = 0;

        for (Card card : hand) {
            if (card.rank() == Rank.ACE) {
                aceCount++;
                currentSum += 11;
            } else {
                currentSum += card.getBaseValue();
            }
        }

        while (currentSum > 21 && aceCount > 0) {
            currentSum -= 10;
            aceCount--;
        }

        return currentSum;
    }
}
