package com.blackjack.blackjack.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.blackjack.blackjack.common.GameConstants.NUM_DECKS;
import static org.junit.jupiter.api.Assertions.*;

class DeckTest {

    @Test
    @DisplayName("A pakli létrehozásakor a méretének 52 * NUM_DECKS-nek kell lennie")
    void shouldCreateDeckWithCorrectSize() {
        Deck deck = Deck.createNewDeck();
        int expectedSize = 52 * NUM_DECKS;

        assertEquals(expectedSize, deck.getDeckLength(), "A kezdeti pakliméret nem megfelelő!");
    }

    @Test
    @DisplayName("Laposztásnál az eredeti pakli nem változhat meg (Immutabilitás)")
    void dealCardShouldNotModifyOriginalDeck() {
        Deck originalDeck = Deck.createNewDeck();
        int initialSize = originalDeck.getDeckLength();

        originalDeck.dealCard(); // Nem mentjük el az eredményt

        // Az eredeti pakli hossza változatlan marad!
        assertEquals(initialSize, originalDeck.getDeckLength(), "Az eredeti pakli megváltozott!");
    }

    @Test
    @DisplayName("Laposztás után az új pakli hossza eggyel csökken")
    void dealCardShouldReturnNewDeckWithReducedSize() {
        Deck deck = Deck.createNewDeck();
        int initialSize = deck.getDeckLength();

        DealResult result = deck.dealCard();
        Deck newDeck = result.newDeck();

        assertEquals(initialSize - 1, newDeck.getDeckLength(), "Az új pakli mérete nem csökkent!");
        assertNotNull(result.dealtCard(), "A kiosztott lap nem lehet null!");
    }

    @Test
    @DisplayName("A kiosztott lapnak a pakli első lapjának kell lennie")
    void dealtCardShouldBeTheFirstCardOfOriginalDeck() {
        Deck deck = Deck.createNewDeck();
        Card firstCard = deck.deck().get(0);

        DealResult result = deck.dealCard();

        assertEquals(firstCard, result.dealtCard(), "Nem a pakli első lapját osztottuk ki!");
    }

    @Test
    @DisplayName("Hibaüzenetet kell kapnunk, ha üres pakliból akarunk osztani")
    void shouldThrowExceptionWhenDealingFromEmptyDeck() {
        // Üres pakli gyártása builderrel
        Deck emptyDeck = Deck.builder().deck(List.of()).build();

        assertThrows(IllegalStateException.class, emptyDeck::dealCard,
            "Az üres paklinak hibát kellene dobnia osztáskor!");
    }

    @Test
    @DisplayName("A pakli kimerítése (stressz teszt láncolt állapotokkal)")
    void testStateChainingUntilDeckIsEmpty() {
        Deck currentDeck = Deck.createNewDeck();
        int totalCards = currentDeck.getDeckLength();

        // Végigzongorázzuk a teljes paklit
        for (int i = 0; i < totalCards; i++) {
            DealResult result = currentDeck.dealCard();
            currentDeck = result.newDeck(); // Mindig frissítjük a referenciát
        }

        assertEquals(0, currentDeck.getDeckLength());
        assertTrue(currentDeck.deck().isEmpty());
    }
}
