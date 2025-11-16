package com.blackjack.blackjack.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.blackjack.blackjack.common.GameConstants.INITIAL_DECK_LENGTH;
import static com.blackjack.blackjack.common.GameConstants.NUM_DECKS;

public class Deck implements Serializable {
    private final List<Card> deck;

    public Deck() {
        this.deck = createDeck();
    }

    private List<Card> createDeck() {
        List<Card> singleDeck = Arrays.stream(Suit.values())
            .flatMap(suit -> Arrays.stream(Rank.values())
                .map(rank -> new Card(suit, rank)))
            .collect(Collectors.toList());

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
}
