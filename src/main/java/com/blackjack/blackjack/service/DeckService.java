package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.DealResult;
import com.blackjack.blackjack.model.Deck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Felelős a Deck objektumokkal való interakcióért,
 * különösen a laposztásért.
 * Nincs felelőssége a GameState mentéséért.
 * Az immutable Deck modellel dolgozik.
 */
@Service
public class DeckService {
    private static final Logger logger = LoggerFactory.getLogger(DeckService.class);

    public DealResult dealCard(Deck deck) {
        if (deck == null) {
            logger.error("Deck is null.");
            throw new IllegalStateException("Cannot deal card from a null deck.");
        }

        DealResult result = deck.dealCard();

        logger.debug(">>>>>>>  #######  Dealt card: {} - Remaining deck size: {}",
            result.dealtCard(),
            result.newDeck().getDeckLength());

        return result;
    }

    public Deck createNewDeck() {
        return Deck.createNewDeck();
    }
}
