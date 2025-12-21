package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.HandState;
import org.springframework.stereotype.Service;

@Service
public class HandStateUpdater {
    public HandState updateHandState(int sum, int handSize, boolean isSplitHand) {
        if (sum > 21) {
            return HandState.BUST;
        }
        if (sum == 21) {
            if (handSize == 2 && !isSplitHand) {
                return HandState.BLACKJACK;
            } else {
                return HandState.TWENTY_ONE;
            }
        }

        return HandState.UNDER_21;
    }
}
