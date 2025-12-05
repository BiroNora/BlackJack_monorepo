package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.HandState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HandStateUpdaterTest {
    @Test
    void testBustState() {
        HandState state = HandStateUpdater.updateHandState(22, 3);
        assertEquals(HandState.BUST, state);

        state = HandStateUpdater.updateHandState(30, 5);
        assertEquals(HandState.BUST, state);
    }

   @Test
    void testNaturalBlackjackState() {
        HandState state = HandStateUpdater.updateHandState(21, 2);
        assertEquals(HandState.BLACKJACK, state);
    }

    @Test
    void testTwentyOneState() {
        HandState state = HandStateUpdater.updateHandState(21, 3);
        assertEquals(HandState.TWENTY_ONE, state);

        state = HandStateUpdater.updateHandState(21, 4);
        assertEquals(HandState.TWENTY_ONE, state);
    }

    @Test
    void testUnderTwentyOneState() {
        HandState state = HandStateUpdater.updateHandState(17, 3);
        assertEquals(HandState.UNDER_21, state);

        state = HandStateUpdater.updateHandState(20, 2);
        assertEquals(HandState.UNDER_21, state);

        state = HandStateUpdater.updateHandState(0, 0);
        assertEquals(HandState.UNDER_21, state);
    }
}
