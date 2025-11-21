package com.blackjack.blackjack.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.io.Serializable;

public record Card(Suit suit, Rank rank) implements Serializable {
    private static final Suit MASKED_SUIT = Suit.fromSymbol(" ");
    private static final Rank MASKED_RANK = Rank.fromSymbol("✪");

    /**
     * @JsonCreator: Statikus gyári metódus, ami Jacksonnak megmondja,
     * hogyan építse fel a Card objektumot a Redisből érkező stringből (pl. "♥A").
     */
    @JsonCreator
    public static Card of(String cardString) {
        if (cardString == null || cardString.length() < 2) {
            throw new IllegalArgumentException("Invalid card string format: " + cardString);
        }

        String suitSymbol = cardString.substring(0, 1);

        String rankSymbol = cardString.substring(1);

        Suit suit = Suit.fromSymbol(suitSymbol);
        Rank rank = Rank.fromSymbol(rankSymbol);

        return new Card(suit, rank);
    }

    public static Card createMaskedCard() {
        return new Card(MASKED_SUIT, MASKED_RANK);
    }

    public int getBaseValue() {
        return rank.getBaseValue();
    }

    /**
     * @JsonValue: Ez a metódus generálja a kártya string reprezentációját (pl. "♥A").
     * Ezt használja Jackson a Redisbe történő szerializáláshoz.
     */
    @Override
    @JsonValue
    public String toString() {
        return suit.getSymbol() + rank.getSymbol();
    }
}
