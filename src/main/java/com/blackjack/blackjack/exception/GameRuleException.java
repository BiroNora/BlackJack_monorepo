package com.blackjack.blackjack.exception;

/**
 * Kivétel, amely a játékszabályok megsértését jelzi.
 * Pl. Tétrakás a megengedett tartományon kívül, vagy split kísérlet érvénytelen lapokkal.
 */
public class GameRuleException extends RuntimeException {

    // Konstruktor, amely egy hibaüzenetet fogad (a hiba leírását)
    public GameRuleException(String message) {
        super(message);
    }

    // Konstruktor, amely egy hibaüzenetet és az okozó kivételt fogad
    public GameRuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
