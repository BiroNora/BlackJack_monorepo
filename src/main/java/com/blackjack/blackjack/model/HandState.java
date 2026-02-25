package com.blackjack.blackjack.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum HandState {
    NONE(0),
    UNDER_21(10),
    BLACKJACK(11), // Eredeti 2 lapos Black Jack
    TWENTY_ONE(8), // Splitelt vagy többlapos 21
    BUST(9);

    private final int value;

    HandState(int value) {
        this.value = value;
    }

    @JsonValue
    public int getValue() {
        return value;
    }

    @JsonCreator // Ez legyen a statikus metóduson!
    public static HandState fromValue(int value) {
        for (HandState state : HandState.values()) {
            if (state.value == value) {
                return state;
            }
        }
        return NONE; // Vagy dobhatsz hibát is
    }
}
