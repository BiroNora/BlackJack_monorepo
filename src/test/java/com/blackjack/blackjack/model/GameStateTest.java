package com.blackjack.blackjack.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GameStateTest {
    @Test
    @DisplayName("A Buildernek helyesen kell inicializálnia minden mezőt")
    void testGameStateBuilder() {
        UUID clientId = UUID.randomUUID();
        Deck deck = Deck.createNewDeck();

        GameState state = GameState.builder()
            .clientId(clientId)
            .deck(deck)
            .isRoundActive(true)
            .betList(List.of(10, 20))
            .player(PlayerHand.builder().sum(10).build())
            .build();

        assertEquals(clientId, state.getClientId());
        assertTrue(state.isRoundActive());
        assertEquals(2, state.getBetList().size());
        assertEquals(10, state.getPlayer().sum());
        assertEquals(deck.getDeckLength(), state.getDeckLen());
    }

    @Test
    @DisplayName("A toBuilder-nek mélymásolatot/új példányt kell készítenie")
    void testToBuilderImmutability() {
        GameState original = GameState.builder()
            .bet(100)
            .handCounter(1)
            .build();

        // Módosítjuk az állapotot toBuilderrel
        GameState updated = original.toBuilder()
            .bet(200)
            .build();

        // Ellenőrizzük, hogy az eredeti nem változott (Immutabilitás)
        assertNotSame(original, updated);
        assertEquals(100, original.getBet());
        assertEquals(200, updated.getBet());
        // A nem módosított mező megmarad
        assertEquals(1, updated.getHandCounter());
    }

    @Test
    @DisplayName("Segédmetódusok kalkulációinak ellenőrzése")
    void testCalculatedFields() {
        GameState state = GameState.builder()
            .handCounter(5)
            .splitReq(2)
            .build();

        assertEquals(6, state.calculateNextHandCounter());
        assertEquals(5, state.calculateNewSplitReq(3));
        // Fontos: a metódus csak számol, nem írja felül a mezőt!
        assertEquals(5, state.getHandCounter());
    }

    @Test
    @DisplayName("getDeckLen kezelje a null deck esetet")
    void testGetDeckLenWithNull() {
        GameState state = GameState.builder().deck(null).build();
        assertEquals(0, state.getDeckLen());
    }

    @Test
    @DisplayName("Default értékek ellenőrzése (Mapok)")
    void testDefaultValues() {
        GameState state = GameState.builder().build();

        assertNotNull(state.getPlayers());
        assertNotNull(state.getPlayersIndex());
        assertTrue(state.getPlayers().isEmpty());
    }
}
