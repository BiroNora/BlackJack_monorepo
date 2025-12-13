package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.SessionContext;
import com.blackjack.blackjack.exception.GlobalExceptionHandler;
import com.blackjack.blackjack.model.*;
import com.blackjack.blackjack.repository.PlayerRepository;
import com.blackjack.blackjack.service.AuthenticationService;
import com.blackjack.blackjack.service.GameService;
import com.blackjack.blackjack.service.GameStateService;
import com.blackjack.blackjack.service.PlayerService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static com.blackjack.blackjack.common.GameConstants.NUM_DECKS;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class GameControllerTest {
    private static final UUID CLIENT_UUID = UUID.randomUUID();
    private static final Player TEST_PLAYER = new Player(CLIENT_UUID, 1000);
    private final UUID testClientId = TEST_PLAYER.getClientId();
    private static final GameState MOCKED_INITIAL_GAME_STATE = GameState.builder()
        .clientId(CLIENT_UUID)
        .player(PlayerHand.createEmptyHand())
        .dealerMasked(DealerHandMasked.createEmptyHand())
        .dealerUnmasked(DealerHandUnmasked.createEmptyHand())
        .deck(null)
        .bet(0)
        .betList(new ArrayList<>())
        .isRoundActive(false)
        .build();
    @InjectMocks
    private GameController gameController;
    @Mock private AuthenticationService authenticationService;
    @Mock private GameService gameService;
    @Mock private PlayerService playerService;
    @Mock private GameStateService gameStateService;
    @Mock private PlayerRepository playerRepository;
    private MockHttpSession springMockSession;
    private MockMvc mockMvc;
    private SessionContext mockContext;

    @BeforeEach
    void setup() {
        // 1. MockMvc inicializálása
        this.mockMvc = MockMvcBuilders.standaloneSetup(gameController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        // 2. Spring-specifikus session létrehozása
        this.springMockSession = new MockHttpSession();
        this.springMockSession.setAttribute("gameState", MOCKED_INITIAL_GAME_STATE); // Beállítjuk a kiinduló állapotot

        // 3. SessionContext létrehozása
        this.mockContext = new SessionContext(
            testClientId,
            TEST_PLAYER,
            springMockSession,
            Optional.of(MOCKED_INITIAL_GAME_STATE)
        );

        // 4. AuthenticationService beállítása
        when(authenticationService.validateAndGetContext(any(HttpSession.class)))
            .thenReturn(this.mockContext);
    }

    @Test
    void createDeck_shouldReturnOkAndCallService_whenSuccessful() throws Exception {
        final int EXPECTED_DECK_LEN = 52 * NUM_DECKS;
        final int EXPECTED_TOKENS = TEST_PLAYER.getTokens(); // 1000

        // 1. ARRANGE: A Service által visszaadandó GameState MODELL
        // Ez a teljes objektum, amit a Controller a sessionba ment és amiből a DTO-t építi.
        GameState MOCKED_SERVICE_RETURN_MODEL = GameState.builder()
            .clientId(CLIENT_UUID)
            // A Deck() példányosítása kritikus, hogy a getDeckLen() 52-t adjon.
            .deck(new Deck())
            .player(PlayerHand.createEmptyHand())
            .dealerMasked(DealerHandMasked.createEmptyHand())
            .dealerUnmasked(DealerHandUnmasked.createEmptyHand())
            .bet(0)
            .betList(new ArrayList<>())
            .isRoundActive(false)
            .build();

        // 2. MOCK: A Service a GameState MODELLT adja vissza.
        when(gameService.createNewDeck(any())).thenReturn(MOCKED_SERVICE_RETURN_MODEL);

        // 3. ACT & ASSERT
        // Ha a mockMvc inicializálva van, ez már le fog futni
        mockMvc.perform(post("/api/create_deck")
                .session(springMockSession))

            .andExpect(status().isOk())

            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.game_state_hint").value("DECK_CREATED"))
            .andExpect(jsonPath("$.current_tokens").value(EXPECTED_TOKENS))
            .andExpect(jsonPath("$.game_state.deckLen").value(EXPECTED_DECK_LEN));
        // 4. VERIFY: Service hívás
        verify(gameService).createNewDeck(any());

        // 5. VERIFY: Session ellenőrzése (NEM MOCKITO, hanem Assertions)
        GameState savedState = (GameState) springMockSession.getAttribute("gameState");
        // Assertions.assertEquals a teljes GameState MODELLT ellenőrzi!
        Assertions.assertEquals(MOCKED_SERVICE_RETURN_MODEL, savedState,
            "A GameState MODELLT megfelelően be kell állítani a session attribútumban.");
    }
}
