package com.blackjack.blackjack.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HandStateTest {
    public static HandState valueOf(int value) {
        for (HandState state : HandState.values()) {
            if (state.getValue() == value) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown HandState value: " + value);
    }

    @Test
    void testEnumValuesAndConstants() {
        assertEquals(0, HandState.NONE.getValue());

        assertEquals(10, HandState.UNDER_21.getValue());
        assertEquals(11, HandState.BLACKJACK.getValue());
        assertEquals(8, HandState.TWENTY_ONE.getValue());
        assertEquals(9, HandState.BUST.getValue());
    }

    @Test
    void testJsonCreatorMapping() {
        assertEquals(HandState.NONE, valueOf(0));
        assertEquals(HandState.UNDER_21, valueOf(10));
        assertEquals(HandState.BLACKJACK, valueOf(11));
        assertEquals(HandState.TWENTY_ONE, valueOf(8));
        assertEquals(HandState.BUST, valueOf(9));
    }

    @Test
    void testInvalidValueThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> valueOf(99));
    }
}
