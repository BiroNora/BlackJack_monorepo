package com.blackjack.blackjack.model;

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

    public int getValue() {
        return value;
    }
}
