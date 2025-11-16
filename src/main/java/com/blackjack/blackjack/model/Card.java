package com.blackjack.blackjack.model;

import java.io.Serializable;

public class Card implements Serializable {
    private final Suit suit;
    private final Rank rank;

    public Card(Suit suit, Rank rank) {
        this.suit = suit;
        this.rank = rank;
    }

    public int getBaseValue() {
        return rank.getBaseValue();
    }

    public Suit getSuit() {
        return suit;
    }

    public Rank getRank() {
        return rank;
    }

    @Override
    public String toString() {
        return suit.getSymbol() + rank.getSymbol();
    }
}
