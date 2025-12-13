package com.blackjack.blackjack.service;

import com.blackjack.blackjack.dto.SessionContext;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.repository.PlayerRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthenticationServiceTest {
    private final UUID TEST_CLIENT_ID = UUID.randomUUID();
    @InjectMocks
    private AuthenticationService authenticationService;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private GameStateService gameStateService;
    @Mock
    private HttpSession mockSession;
    private Player testPlayer;
    private GameState testGameState;

    @BeforeEach
    void setUp() {
        // Alapértelmezett mock objektumok előkészítése
        testPlayer = new Player();
        testPlayer.setClientId(TEST_CLIENT_ID);
        // Dummy GameState a sikeres tesztekhez
        testGameState = new GameState();
    }

    // --------------------------------------------------------------------------------
    // 1. Client ID Validáció Tesztek
    // --------------------------------------------------------------------------------

    @Test
    void validateAndGetContext_shouldThrowForbiddenWhenClientIdIsMissing() {
        // Előkészítés: session.getAttribute("clientId") -> null
        when(mockSession.getAttribute("clientId")).thenReturn(null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> authenticationService.validateAndGetContext(mockSession),
            "Hibát kell dobnia, ha a ClientId hiányzik.");

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode(), "A HTTP kódnak 403-nak kell lennie.");
        // Ellenőrzés: A DB-t nem is hívtuk meg
        verify(playerRepository, never()).findByClientId(any());
    }

    @Test
    void validateAndGetContext_shouldThrowForbiddenWhenClientIdIsInvalidFormat() {
        // Előkészítés: session.getAttribute("clientId") -> érvénytelen string
        when(mockSession.getAttribute("clientId")).thenReturn("not-a-uuid");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> authenticationService.validateAndGetContext(mockSession),
            "Hibát kell dobnia, ha a ClientId formátuma érvénytelen.");

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode(), "A HTTP kódnak 403-nak kell lennie.");
        verify(playerRepository, never()).findByClientId(any());
    }

    // --------------------------------------------------------------------------------
    // 2. Player Adatbázis Lekérés Tesztek
    // --------------------------------------------------------------------------------

    @Test
    void validateAndGetContext_shouldThrowNotFoundWhenPlayerIsMissing() {
        // Előkészítés 1: Session tartalmazza az érvényes UUID-t
        when(mockSession.getAttribute("clientId")).thenReturn(TEST_CLIENT_ID.toString());

        // Előkészítés 2: A Repository nem találja a játékost
        when(playerRepository.findByClientId(TEST_CLIENT_ID)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> authenticationService.validateAndGetContext(mockSession),
            "Hibát kell dobnia, ha a Játékos nem található a DB-ben.");

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode(), "A HTTP kódnak 404-nek kell lennie.");
        // Ellenőrzés: A DB-t meghívtuk, a GameStateService-t nem (a Player hiány miatt)
        verify(playerRepository, times(1)).findByClientId(TEST_CLIENT_ID);
        verify(gameStateService, never()).getGameState(any());
    }


    // --------------------------------------------------------------------------------
    // 3. Sikeres Esetek Tesztek
    // --------------------------------------------------------------------------------

    @Test
    void validateAndGetContext_shouldReturnContextWithExistingGameState() {
        // Előkészítés 1: Session & Player megtalálva
        when(mockSession.getAttribute("clientId")).thenReturn(TEST_CLIENT_ID.toString());
        when(playerRepository.findByClientId(TEST_CLIENT_ID)).thenReturn(Optional.of(testPlayer));

        // Előkészítés 2: GameState is megtalálva a Redisben
        when(gameStateService.getGameState(TEST_CLIENT_ID)).thenReturn(Optional.of(testGameState));

        // Akció
        SessionContext context = authenticationService.validateAndGetContext(mockSession);

        // Ellenőrzés
        assertNotNull(context, "A SessionContext nem lehet null.");
        assertEquals(TEST_CLIENT_ID, context.clientId(), "A Client ID-nak meg kell egyeznie.");
        // *** JAVÍTVA: gameStateOptional ***
        assertTrue(context.gameStateOptional().isPresent(), "A GameState-nek jelen kell lennie.");
        assertEquals(testGameState, context.gameStateOptional().get(), "A GameState objektumoknak meg kell egyezniük.");

        verify(playerRepository, times(1)).findByClientId(TEST_CLIENT_ID);
        verify(gameStateService, times(1)).getGameState(TEST_CLIENT_ID);
    }

    @Test
    void validateAndGetContext_shouldReturnContextWithEmptyGameState() {
        // Előkészítés 1: Session & Player megtalálva
        when(mockSession.getAttribute("clientId")).thenReturn(TEST_CLIENT_ID.toString());
        when(playerRepository.findByClientId(TEST_CLIENT_ID)).thenReturn(Optional.of(testPlayer));

        // Előkészítés 2: GameState NEM található a Redisben (üres Optional)
        when(gameStateService.getGameState(TEST_CLIENT_ID)).thenReturn(Optional.empty());

        // Akció
        SessionContext context = authenticationService.validateAndGetContext(mockSession);

        // Ellenőrzés
        assertNotNull(context);
        assertEquals(TEST_CLIENT_ID, context.clientId());
        // *** JAVÍTVA: gameStateOptional ***
        assertFalse(context.gameStateOptional().isPresent(), "A GameState-nek hiányoznia kell.");

        verify(playerRepository, times(1)).findByClientId(TEST_CLIENT_ID);
        verify(gameStateService, times(1)).getGameState(TEST_CLIENT_ID);
    }
}
