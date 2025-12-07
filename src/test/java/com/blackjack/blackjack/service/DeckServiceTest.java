package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.Card;
import com.blackjack.blackjack.model.Deck;
import com.blackjack.blackjack.model.Rank;
import com.blackjack.blackjack.model.Suit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DeckServiceTest {
    @InjectMocks
    private DeckService deckService;
    private final Card testCard = new Card(Suit.HEARTS, Rank.TEN);

    @Test
    void testCreateNewDeckShouldReturnNewDeckInstance() {
        Deck deck = deckService.createNewDeck();

        assertNotNull(deck);
    }

    @Test
    void testDealCardShouldReturnCardAndCallDeckDealCard(@Mock Deck mockDeck) {
        when(mockDeck.dealCard()).thenReturn(testCard);
        when(mockDeck.getDeckLength()).thenReturn(51);

        Card dealtCard = deckService.dealCard(mockDeck);

        assertNotNull(dealtCard);
        assertEquals(testCard, dealtCard);
        verify(mockDeck).dealCard();
    }

    @Test
    void testDealCardShouldThrowExceptionWhenDeckIsNull() {
        assertThrows(IllegalStateException.class, () -> deckService.dealCard(null));
    }

    @Test
    void testDealCardShouldForwardDeckException(@Mock Deck mockDeck) {
        when(mockDeck.dealCard()).thenThrow(new IllegalStateException("Deck is empty."));

        assertThrows(IllegalStateException.class, () -> deckService.dealCard(mockDeck));
    }
}
