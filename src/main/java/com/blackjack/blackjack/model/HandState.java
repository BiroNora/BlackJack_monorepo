package com.blackjack.blackjack.model;

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

    public int getValue() {
        return value;
    }
}
