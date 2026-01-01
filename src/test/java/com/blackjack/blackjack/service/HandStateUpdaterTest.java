package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.HandState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class HandStateUpdaterTest {

    @InjectMocks
    private HandStateUpdater handStateUpdater;

    @Test
    void testBustState() {
        HandState state = handStateUpdater.updateHandState(22, 3, false);
        assertEquals(HandState.BUST, state);

        state = handStateUpdater.updateHandState(30, 5, true);
        assertEquals(HandState.BUST, state);
    }

    @Test
    void testNaturalBlackjackState() {
        HandState state = handStateUpdater.updateHandState(21, 2, false);
        assertEquals(HandState.BLACKJACK, state);
    }

    @Test
    void testTwentyOneState() {
        // Splitelt 2 lapos 21 -> TWENTY_ONE
        HandState state = handStateUpdater.updateHandState(21, 2, true);
        assertEquals(HandState.TWENTY_ONE, state);

        // Splitelt 3 lapos 21 -> TWENTY_ONE
        HandState state1 = handStateUpdater.updateHandState(21, 3, true);
        assertEquals(HandState.TWENTY_ONE, state1);

        // Nem splitelt 4 lapos 21 -> TWENTY_ONE
        HandState state2 = handStateUpdater.updateHandState(21, 4, false);
        assertEquals(HandState.TWENTY_ONE, state2);
    }

    @Test
    void testUnderTwentyOneState() {
        HandState state = handStateUpdater.updateHandState(17, 3, false);
        assertEquals(HandState.UNDER_21, state);

        state = handStateUpdater.updateHandState(20, 2, true);
        assertEquals(HandState.UNDER_21, state);

        state = handStateUpdater.updateHandState(0, 0, false);
        assertEquals(HandState.UNDER_21, state);
    }

    @Test
    @DisplayName("Szabály-specifikus határesetek: Split vs Natural Blackjack")
    void testSpecialRules() {
        assertEquals(HandState.BLACKJACK, handStateUpdater.updateHandState(21, 2, false));

        assertEquals(HandState.TWENTY_ONE, handStateUpdater.updateHandState(21, 2, true));

        assertEquals(HandState.TWENTY_ONE, handStateUpdater.updateHandState(21, 3, false));
    }
}
