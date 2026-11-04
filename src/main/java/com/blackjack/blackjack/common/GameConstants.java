package com.blackjack.blackjack.common;

import java.util.regex.Pattern;

public class GameConstants {
    public static final int MINIMUM_BET = 1;
    public static final int INITIAL_TOKENS = 1000;
    public static final int CARDS_IN_DECK = 52;
    public static final int NUM_DECKS = 2;
    public static final int INITIAL_DECK_LENGTH = NUM_DECKS * CARDS_IN_DECK;
    public static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,25}$");
}
