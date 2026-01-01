package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DeckServiceTest {

    private final Card testCard = new Card(Suit.HEARTS, Rank.TEN);
    @InjectMocks
    private DeckService deckService;

    @Test
    @DisplayName("Új pakli létrehozása nem null")
    void testCreateNewDeckShouldReturnNewDeckInstance() {
        Deck deck = deckService.createNewDeck();
        assertNotNull(deck);
        assertTrue(deck.getDeckLength() > 0);
    }

    @Test
    @DisplayName("Laposztáskor a service továbbítja a DealResult-ot")
    void testDealCardShouldReturnDealResult(@Mock Deck mockDeck) {
        // 1. Előkészítjük a várt eredményt (egy kártya + egy új pakli objektum)
        Deck newDeckState = Deck.builder().deck(List.of()).build(); // egy üres új pakli állapot
        DealResult expectedResult = new DealResult(testCard, newDeckState);

        // 2. Mockoljuk a pakli hívását
        when(mockDeck.dealCard()).thenReturn(expectedResult);

        // 3. Futtatás
        DealResult actualResult = deckService.dealCard(mockDeck);

        // 4. Ellenőrzés: a Service változtatás nélkül adja-e tovább, amit a paklitól kapott
        assertNotNull(actualResult);
        assertEquals(testCard, actualResult.dealtCard());
        assertEquals(newDeckState, actualResult.newDeck());

        verify(mockDeck, times(1)).dealCard();
    }

    @Test
    @DisplayName("Null pakli esetén hiba dobása")
    void testDealCardShouldThrowExceptionWhenDeckIsNull() {
        assertThrows(IllegalStateException.class, () -> deckService.dealCard(null));
    }

    @Test
    @DisplayName("A pakli által dobott kivétel továbbterjed")
    void testDealCardShouldForwardDeckException(@Mock Deck mockDeck) {
        when(mockDeck.dealCard()).thenThrow(new IllegalStateException("Deck is empty."));

        assertThrows(IllegalStateException.class, () -> deckService.dealCard(mockDeck));
    }
}
