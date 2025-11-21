package com.blackjack.blackjack.model;

import java.util.Arrays;

public enum Suit {
    HEARTS("♥"),
    DIAMONDS("♦"),
    CLUBS("♣"),
    SPADES("♠"),
    MASKED_SUIT(" ");

    private final String symbol;

    Suit(String symbol) {
        this.symbol = symbol;
    }

    public static Suit fromSymbol(String symbol) {
        return Arrays.stream(Suit.values())
            .filter(suit -> suit.getSymbol().equals(symbol))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Invalid suit symbol: " + symbol));
    }

    public String getSymbol() {
        return symbol;
    }
}
