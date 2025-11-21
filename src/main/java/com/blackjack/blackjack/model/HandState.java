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

    @JsonCreator
    HandState(int value) {
        this.value = value;
    }

    @JsonValue
    public int getValue() {
        return value;
    }
}
