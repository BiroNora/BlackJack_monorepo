package com.blackjack.blackjack.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.blackjack.blackjack.common.GameConstants.NUM_DECKS;

@JsonIgnoreProperties(ignoreUnknown = true)
@Value
@Builder(toBuilder = true)
@Jacksonized
public class Deck implements Serializable {

    // A @Value által beállított final mező
    private final List<Card> deck;

    public static Deck createNewDeck() {
        // Ez hívja meg a belső keverő logikát
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

        // 1. Kimentjük az elosztott lapot.
        Card dealtCard = this.deck.get(0);

        // 2. Létrehozzuk az új listát a maradék lapokkal (ez NEM módosítja az eredeti List<Card>-ot!).
        // A subList nem tartalmazza a 'to' indexet (a size a vége), így az első elemet kihagyja.
        List<Card> remainingCards = new ArrayList<>(this.deck.subList(1, this.deck.size()));

        // 3. Létrehozzuk az ÚJ Deck objektumot a Builder segítségével.
        // Ez csak a 'deck' mezőt frissíti, minden más mező változatlan marad.
        Deck newDeck = this.toBuilder()
            .deck(Collections.unmodifiableList(remainingCards)) // Új Listát állítunk be
            .build();

        return new DealResult(dealtCard, newDeck);
    }

    // --- Megmaradó Funkciók ---

    // Az @Value generálja a getDeck()-et

    public int getDeckLength() {
        return this.deck != null ? this.deck.size() : 0;
    }

    // Az @Value generálja a korrekt toString(), equals(), és hashCode() metódusokat.
}
