package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.Card;
import com.blackjack.blackjack.model.Deck;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Felelős a Deck objektumokkal való interakcióért,
 * különösen a laposztásért.
 * Nincs felelőssége a GameState mentéséért.
 */
@Service
public class DeckService {
    private static final Logger logger = LoggerFactory.getLogger(DeckService.class);

    public Card dealCard(Deck deck) {
        if (deck == null) {
            logger.error("Deck is null.");
            throw new IllegalStateException("Cannot deal card from a null deck.");
        }

        Card card = deck.dealCard();
        logger.debug("Dealt card: {} - Remaining deck size: {}", card, deck.getDeckLength());
        return card;
    }

    public Deck createNewDeck() {
        return new Deck();
    }
}
