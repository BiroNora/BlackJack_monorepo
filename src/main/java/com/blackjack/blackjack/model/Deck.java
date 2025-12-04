package com.blackjack.blackjack.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.blackjack.blackjack.common.GameConstants.NUM_DECKS;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Deck implements Serializable {
    private final List<Card> deck;

    public Deck() {
        this.deck = createDeck();
    }

    private List<Card> createDeck() {
        List<Card> singleDeck = Arrays.stream(Suit.values())
            .filter(suit -> suit != Suit.MASKED_SUIT)
            .flatMap(suit -> Arrays.stream(Rank.values())
                .filter(rank -> rank != Rank.MASKED_RANK)
                .map(rank -> new Card(suit, rank)))
            .toList();

        List<Card> fullDeck = new ArrayList<>();

        for (int i = 0; i < NUM_DECKS; i++) {
            fullDeck.addAll(singleDeck);
        }

        Collections.shuffle(fullDeck);

        return fullDeck;
    }

    public List<Card> getDeck() {
        return deck;
    }

    public int getDeckLength() {
        return this.deck.size();
    }

    public Card dealCard() {
        if (deck.isEmpty()) {
            throw new IllegalStateException("Deck is empty. Cannot deal card.");
        }
        return deck.remove(0);
    }

    @Override
    public String toString() {
        return "Deck{" +
            "deck=" + deck +
            '}';
    }
}
