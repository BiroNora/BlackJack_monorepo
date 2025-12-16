package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.Deck;
import com.blackjack.blackjack.model.DealResult; // 🚨 Új import
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Felelős a Deck objektumokkal való interakcióért,
 * különösen a laposztásért.
 * Nincs felelőssége a GameState mentéséért.
 * * Jelenleg az immutable Deck modellel dolgozik.
 */
@Service
public class DeckService {
    private static final Logger logger = LoggerFactory.getLogger(DeckService.class);

    // 🚨 Változás: A metódus immár DealResult-ot ad vissza a Card helyett.
    // Ez tartalmazza a húzott lapot és az ÚJ DECK állapotot is!
    public DealResult dealCard(Deck deck) {
        if (deck == null) {
            logger.error("Deck is null.");
            throw new IllegalStateException("Cannot deal card from a null deck.");
        }

        // 🚨 Változás: A hívás immutabilis, a 'deck' objektum nem változik!
        DealResult result = deck.dealCard();

        // A naplózáshoz az új állapotot használjuk a DealResult-ból
        logger.debug(">>>>>>>  #######  Dealt card: {} - Remaining deck size: {}",
            result.dealtCard(),
            result.newDeck().getDeckLength());

        return result;
    }

    // 🚨 Változás: A Deck immutabilis lett, így a statikus gyári metódust hívjuk.
    public Deck createNewDeck() {
        return Deck.createNewDeck(); // A Deck osztályban definiált statikus metódus
    }
}
