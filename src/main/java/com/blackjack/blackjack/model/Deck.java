package com.blackjack.blackjack.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.blackjack.blackjack.common.GameConstants.NUM_DECKS;

@JsonIgnoreProperties(ignoreUnknown = true)
@Builder(toBuilder = true)
@Jacksonized
public record Deck(List<Card> deck) implements Serializable {

    public static Deck createNewDeck() {
        return Deck.builder()
            .deck(createShuffledDeckList())
            .build();
    }

    private static List<Card> createShuffledDeckList() {
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
        return Collections.unmodifiableList(fullDeck);
    }

    public DealResult dealCard() {
        if (deck == null || deck.isEmpty()) {
            throw new IllegalStateException("Deck is empty. Cannot deal card.");
        }

        Card dealtCard = this.deck.getFirst();

        List<Card> remainingCards = new ArrayList<>(this.deck.subList(1, this.deck.size()));

        Deck newDeck = this.toBuilder()
            .deck(Collections.unmodifiableList(remainingCards)) // Új Listát állítunk be
            .build();

        return new DealResult(dealtCard, newDeck);
    }

    public int getDeckLength() {
        return this.deck != null ? this.deck.size() : 0;
    }
}
