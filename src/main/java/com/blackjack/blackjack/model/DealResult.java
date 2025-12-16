package com.blackjack.blackjack.model;

import lombok.Value;

@Value
public class DealResult {
    Card dealtCard;
    Deck newDeck;
}
