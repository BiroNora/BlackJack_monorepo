package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.Card;
import com.blackjack.blackjack.model.Rank;

import java.util.List;

public class HandValueCalculator {
    private static final int BLACKJACK_LIMIT = 21;
    private static final int ACE_HIGH_VALUE = 11;
    private static final int ACE_LOW_VALUE = 1;

    public static int calculateSum(List<Card> hand) {
        if (hand == null || hand.isEmpty()) {
            return 0;
        }

        int sum = 0;
        int aceCount = 0;

        for (Card card : hand) {
            Rank rank = card.rank();
            if (rank == Rank.ACE) {
                aceCount++;
                continue;
            }
            sum += rank.getBaseValue();
        }

        for (int i = 0; i < aceCount; i++) {
            if (sum + ACE_HIGH_VALUE <= BLACKJACK_LIMIT) {
                sum += ACE_HIGH_VALUE;
            } else {
                sum += ACE_LOW_VALUE;
            }
        }

        return sum;
    }
}
