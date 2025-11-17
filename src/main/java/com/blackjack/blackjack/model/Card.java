package com.blackjack.blackjack.model;

import java.io.Serializable;

public record Card(Suit suit, Rank rank) implements Serializable {

    public int getBaseValue() {
        return rank.getBaseValue();
    }

    @Override
    public String toString() {
        return suit.getSymbol() + rank.getSymbol();
    }
}
