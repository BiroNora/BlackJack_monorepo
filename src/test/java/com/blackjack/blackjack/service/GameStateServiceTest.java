package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.GameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * A GameStateService unit tesztjei.
 * A teszt a Mockito-t használja a Spring Session és a RequestContextHolder szimulálására,
 * így elszigetelten ellenőrizhető a mentési/lekérdezési logika.
 */
@ExtendWith(MockitoExtension.class)
public class GameStateServiceTest {

    // A tesztelt osztály, amelybe a mock-ok be vannak injektálva
    @InjectMocks
    private GameStateService gameStateService;

    // Session környezet szimulálása
    @Mock
    private ServletRequestAttributes servletRequestAttributes;
    @Mock
    private HttpServletRequest httpServletRequest;
    @Mock
    private HttpSession httpSession;

    // Példa adatok
    private final UUID testClientId = UUID.randomUUID();
    private GameState testGameState;
    private static final String GAME_STATE_ATTR_NAME = "gameState";


    /**
     * A tesztelés előtt minden alkalommal fut:
     * Beállítja a statikus RequestContextHolder-t a mockolt Session eléréséhez.
     */
    @BeforeEach
    void setUp() {
        // Mock GameState létrehozása
        testGameState = new GameState();
        testGameState.setClientId(testClientId);
        testGameState.setRoundActive(true);

        // A Spring környezet mockolása: beállítjuk, hogy RequestContextHolder adja vissza a mockolt attribútumokat
        when(servletRequestAttributes.getRequest()).thenReturn(httpServletRequest);
        RequestContextHolder.setRequestAttributes(servletRequestAttributes);
    }

    // --- Tesztesetek a Mentésre (saveGameState) ---

    @Test
    void saveGameState_shouldSaveToSessionAttribute() {
        // Előkészítés: Mockoljuk a getSession(false) hívást, hogy visszaadja a mock Sessiont
        when(httpServletRequest.getSession(false)).thenReturn(httpSession);

        // Akció: Mentjük a játékállapotot
        gameStateService.saveGameState(testGameState);

        // Ellenőrzés 1: Győződjünk meg róla, hogy a 'gameState' attribútumot beállítottuk a Sessionben
        verify(httpSession, times(1)).setAttribute(GAME_STATE_ATTR_NAME, testGameState);

        // Ellenőrzés 2: Győződjön meg róla, hogy csak egyszer történt meg a Session kérése
        verify(httpServletRequest, times(1)).getSession(false);
    }

    @Test
    void saveGameState_shouldLogIfSessionIsNull() {
        // Előkészítés: Mockoljuk a getSession(false) hívást, hogy NULL-t adjon vissza (eltűnt session)
        when(httpServletRequest.getSession(false)).thenReturn(null);

        // Akció: Mentjük a játékállapotot
        gameStateService.saveGameState(testGameState);

        // Ellenőrzés: Győződjünk meg róla, hogy NEM történt meg az attribútum beállítása (mert nincs session)
        verify(httpSession, never()).setAttribute(eq(GAME_STATE_ATTR_NAME), any());

        // Jelenleg a loggolást a teszt nem tudja ellenőrizni, de ellenőriztük,
        // hogy a kritikus session.setAttribute hívás elmaradt.
    }


    // --- Tesztesetek a Lekérdezésre (getGameState) ---

    @Test
    void getGameState_shouldReturnGameStateWhenSessionHasAttribute() {
        // Előkészítés: Mockoljuk a getSession(false) hívást
        when(httpServletRequest.getSession(false)).thenReturn(httpSession);

        // Előkészítés: Mockoljuk, hogy a session visszaadja a GameState-et
        when(httpSession.getAttribute(GAME_STATE_ATTR_NAME)).thenReturn(testGameState);

        // Akció: Lekérdezzük a játékállapotot
        Optional<GameState> result = gameStateService.getGameState(testClientId);

        // Ellenőrzés:
        assertTrue(result.isPresent(), "Az eredménynek tartalmaznia kell GameState-et.");
        assertEquals(testGameState.getClientId(), result.get().getClientId(), "A lekérdezett GameId-nek meg kell egyeznie.");
        assertEquals(testGameState.isRoundActive(), result.get().isRoundActive(), "Az állapotnak meg kell egyeznie.");

        verify(httpSession, times(1)).getAttribute(GAME_STATE_ATTR_NAME);
    }

    @Test
    void getGameState_shouldReturnEmptyWhenSessionHasNoAttribute() {
        // Előkészítés: Mockoljuk a getSession(false) hívást
        when(httpServletRequest.getSession(false)).thenReturn(httpSession);

        // Előkészítés: Mockoljuk, hogy a session NULL-t ad vissza (nincs még mentve)
        when(httpSession.getAttribute(GAME_STATE_ATTR_NAME)).thenReturn(null);

        // Akció: Lekérdezzük a játékállapotot
        Optional<GameState> result = gameStateService.getGameState(testClientId);

        // Ellenőrzés:
        assertFalse(result.isPresent(), "Az eredménynek üres Optional-nak kell lennie.");

        verify(httpSession, times(1)).getAttribute(GAME_STATE_ATTR_NAME);
    }

    @Test
    void getGameState_shouldReturnEmptyWhenNoActiveSession() {
        // Előkészítés: Mockoljuk a getSession(false) hívást, hogy NULL-t adjon vissza (nincs session)
        when(httpServletRequest.getSession(false)).thenReturn(null);

        // Akció: Lekérdezzük a játékállapotot
        Optional<GameState> result = gameStateService.getGameState(testClientId);

        // Ellenőrzés:
        assertFalse(result.isPresent(), "Az eredménynek üres Optional-nak kell lennie, ha nincs Session.");

        verify(httpServletRequest, times(1)).getSession(false);
    }
}
