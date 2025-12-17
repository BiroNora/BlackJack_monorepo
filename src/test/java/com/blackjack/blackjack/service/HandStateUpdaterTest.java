package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.HandState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HandStateUpdaterTest {
    private HandStateUpdater handStateUpdater;
    @Test
    void testBustState() {
        HandState state = handStateUpdater.updateHandState(22, 3);
        assertEquals(HandState.BUST, state);

        state = handStateUpdater.updateHandState(30, 5);
        assertEquals(HandState.BUST, state);
    }

    @Test
    void testNaturalBlackjackState() {
        HandState state = handStateUpdater.updateHandState(21, 2);
        assertEquals(HandState.BLACKJACK, state);
    }

    @Test
    void testTwentyOneState() {
        HandState state = handStateUpdater.updateHandState(21, 3);
        assertEquals(HandState.TWENTY_ONE, state);

        state = handStateUpdater.updateHandState(21, 4);
        assertEquals(HandState.TWENTY_ONE, state);
    }

    @Test
    void testUnderTwentyOneState() {
        HandState state = handStateUpdater.updateHandState(17, 3);
        assertEquals(HandState.UNDER_21, state);

        state = handStateUpdater.updateHandState(20, 2);
        assertEquals(HandState.UNDER_21, state);

        state = handStateUpdater.updateHandState(0, 0);
        assertEquals(HandState.UNDER_21, state);
    }
}
