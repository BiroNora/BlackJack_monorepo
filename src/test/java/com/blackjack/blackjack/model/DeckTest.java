package com.blackjack.blackjack.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.blackjack.blackjack.common.GameConstants.NUM_DECKS;
import static org.junit.jupiter.api.Assertions.*;

public class DeckTest {
    private Deck deck;

    @BeforeEach
    void setUp() {
        deck = new Deck();
    }

    @Test
    void testDeckCreationAndInitialSize() {
        final int expectedSize = 52 * NUM_DECKS;
        assertEquals(expectedSize, deck.getDeckLength());
    }

    @Test
    void testDealCardReducesDeckSize() {
        final int initialSize = deck.getDeckLength();
        Card dealtCard = deck.dealCard();

        assertNotNull(dealtCard);
        assertEquals(initialSize - 1, deck.getDeckLength());
    }

    @Test
    void testDealtCardIsRemovedByOneCount() {
        // Azonosítjuk, hogy melyik lap lesz a következő a pakliban (Index 0).
        Card cardToDeal = deck.getDeck().get(0);

        // Megszámoljuk az osztandó lap kezdeti előfordulását a pakliban.
        long initialCount = deck.getDeck().stream()
            .filter(card -> card.equals(cardToDeal))
            .count();

        System.out.println("Card to be dealt: " + cardToDeal + " (Initial count: " + initialCount + ")");

        // Laposztás: Itt történik a kritikus mutáció (remove(0))
        deck.dealCard();

        // Megszámoljuk a lap új előfordulását az eltávolítás után
        long finalCount = deck.getDeck().stream()
            .filter(card -> card.equals(cardToDeal))
            .count();

        System.out.println("Count after dealing: " + finalCount);

        assertEquals(initialCount - 1, finalCount);
        assertEquals((52 * NUM_DECKS) - 1, deck.getDeckLength());
    }

    /**
     * Ellenőrzi, hogy a pakli lapjai ténylegesen keverve vannak-e.
     * Megjegyzés: A keverés tesztelése mindig statisztikai jellegű. Itt azt ellenőrizzük,
     * hogy nem egy determinisztikusan rendezett sorrendben jönnek-e a lapok.
     * Ehhez egy rendezett pakli első 5 lapjával hasonlítjuk össze az első 5 lapot.
     */
    @Test
    void testDeckIsShuffled() {
        // Létrehozunk egy rendezett, nem kevert referencia listát (csak az első paklit)
        List<Card> sortedReference = Arrays.stream(Suit.values())
            .filter(suit -> suit != Suit.MASKED_SUIT)
            .flatMap(suit -> Arrays.stream(Rank.values())
                .filter(rank -> rank != Rank.MASKED_RANK)
                .map(rank -> new Card(suit, rank)))
            .limit(5) // Csak az első 5 lapot nézzük
            .collect(Collectors.toList());

        // Lekérjük a tesztelendő, kevert pakli első 5 lapját
        List<Card> shuffledFirstFive = deck.getDeck().stream().limit(5).collect(Collectors.toList());

        // Ellenőrizzük, hogy a kevert lista nem azonos a rendezett referencia listával.
        // Ez 99.9%-ban jelzi a sikeres keverést.
        assertNotEquals(sortedReference, shuffledFirstFive);
    }

    @Test
    void testDealCardThrowsExceptionWhenDeckIsEmpty() {
        // 1. Üresítsük ki a paklit az összes lap kiosztásával
        int totalCards = deck.getDeckLength();
        for (int i = 0; i < totalCards; i++) {
            deck.dealCard();
        }

        // 2. Ellenőrizzük, hogy üres a pakli
        assertEquals(0, deck.getDeckLength());
        // 3. Ellenőrizzük, hogy a következő dealCard() hívás IllegalStateException-t dob
        assertThrows(IllegalStateException.class, deck::dealCard);
    }
}
