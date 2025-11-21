package com.blackjack.blackjack.model;

import java.util.Arrays;

public enum Rank {
    KING(10, "K"),
    QUEEN(10, "Q"),
    JACK(10, "J"),
    TEN(10, "10"),

    // Az Ász kezdő értéke 11.
    ACE(11, "A"),

    NINE(9, "9"),
    EIGHT(8, "8"),
    SEVEN(7, "7"),
    SIX(6, "6"),
    FIVE(5, "5"),
    FOUR(4, "4"),
    THREE(3, "3"),
    TWO(2, "2"),
    MASKED_RANK(0, "✪");

    private final int baseValue;
    private final String symbol;

    Rank(int baseValue, String symbol) {
        this.baseValue = baseValue;
        this.symbol = symbol;
    }

    public static Rank fromSymbol(String symbol) { // <<< Ezt kell hozzáadni
        return Arrays.stream(Rank.values())
            .filter(rank -> rank.getSymbol().equals(symbol))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Invalid rank symbol: " + symbol));
    }

    public int getBaseValue() {
        return baseValue;
    }

    public String getSymbol() {
        return symbol;
    }
}
