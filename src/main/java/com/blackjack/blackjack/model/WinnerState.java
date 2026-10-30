package com.blackjack.blackjack.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum WinnerState {
    NONE(0),

    //Black Jack eredmények
    BLACKJACK_PLAYER_WON(1),
    BLACKJACK_PUSH(2),
    BLACKJACK_DEALER_WON(3),

    // Általános kimenetelek
    PUSH(4),
    PLAYER_LOST(5),
    PLAYER_WON(6),
    DEALER_WON(7);

    private final int value;

    WinnerState(int value) {
        this.value = value;
    }

    @JsonCreator
    public static WinnerState valueOf(int value) {
        for (WinnerState state : WinnerState.values()) {
            if (state.value == value) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown WinnerState value: " + value);
    }

    @JsonValue
    public int getValue() {
        return value;
    }

    public boolean isImmediateStop() {
        return this == BLACKJACK_PLAYER_WON || this == BLACKJACK_PUSH;
    }

    public static boolean isImmediateStop(int value) {
        return value == 1 || value == 2;
    }
}
