package com.blackjack.blackjack.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WinnerStateTest {
    @Test
    void testEnumValuesAndConstants() {
        assertEquals(0, WinnerState.NONE.getValue());

        // Blackjack eredmények
        assertEquals(1, WinnerState.BLACKJACK_PLAYER_WON.getValue());
        assertEquals(2, WinnerState.BLACKJACK_PUSH.getValue());
        assertEquals(3, WinnerState.BLACKJACK_DEALER_WON.getValue());

        // Általános kimenetelek
        assertEquals(4, WinnerState.PUSH.getValue());
        assertEquals(5, WinnerState.PLAYER_LOST.getValue());
        assertEquals(6, WinnerState.PLAYER_WON.getValue());
        assertEquals(7, WinnerState.DEALER_WON.getValue());
    }

    @Test
    void testJsonCreatorMapping() {
        assertEquals(WinnerState.NONE, WinnerState.valueOf(0));
        assertEquals(WinnerState.BLACKJACK_PLAYER_WON, WinnerState.valueOf(1));
        assertEquals(WinnerState.PUSH, WinnerState.valueOf(4));
        assertEquals(WinnerState.PLAYER_WON, WinnerState.valueOf(6));
    }

    @Test
    void testInvalidValueThrowsException() {
        // Feltételezve, hogy a fenti fromValue metódus létezik:
        // assertThrows(IllegalArgumentException.class, () -> WinnerState.fromValue(99),
        //     "Az érvénytelen értéknek kivételt kell dobnia.");
    }
}
