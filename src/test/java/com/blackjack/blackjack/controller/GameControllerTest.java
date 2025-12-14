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

import java.util.*;

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
    private final int EXPECTED_TOKENS = TEST_PLAYER.getTokens();
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

        GameState MOCKED_SERVICE_RETURN_MODEL = GameState.builder()
            .clientId(CLIENT_UUID)
            .deck(new Deck())
            .player(PlayerHand.createEmptyHand())
            .dealerMasked(DealerHandMasked.createEmptyHand())
            .dealerUnmasked(DealerHandUnmasked.createEmptyHand())
            .bet(0)
            .betList(new ArrayList<>())
            .isRoundActive(false)
            .build();

        when(gameService.createNewDeck(any())).thenReturn(MOCKED_SERVICE_RETURN_MODEL);

        mockMvc.perform(post("/api/create_deck")
                .session(springMockSession))

            .andExpect(status().isOk())

            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.game_state_hint").value("DECK_CREATED"))
            .andExpect(jsonPath("$.current_tokens").value(EXPECTED_TOKENS))
            .andExpect(jsonPath("$.game_state.deckLen").value(EXPECTED_DECK_LEN));

        verify(gameService).createNewDeck(any());

        GameState savedState = (GameState) springMockSession.getAttribute("gameState");
        // Assertions.assertEquals a teljes GameState MODELLT ellenőrzi!
        Assertions.assertEquals(MOCKED_SERVICE_RETURN_MODEL, savedState,
            "A GameState MODELLT megfelelően be kell állítani a session attribútumban.");
    }

    @Test
    void initializeNewRound_whenSuccessful() throws Exception {
        Card playerCard1 = new Card(Suit.HEARTS, Rank.FIVE);
        Card playerCard2 = new Card(Suit.HEARTS, Rank.SIX);
        Card dealerCard1 = new Card(Suit.DIAMONDS, Rank.TEN); // Első lap (maszkolt lesz)

        final String EXPECTED_CARD_STRING = playerCard1.toString(); // A várt string: "♥5"
        final String EXPECTED_MASKED_CARD_STRING = Card.createMaskedCard().toString(); // " ✪"

        Card dealerCard2 = new Card(Suit.CLUBS, Rank.EIGHT);

        Deck mockDeck = new Deck();
        mockDeck.dealCard();
        mockDeck.dealCard();
        mockDeck.dealCard();
        mockDeck.dealCard();

        final int EXPECTED_DECK_LEN = mockDeck.getDeckLength();

        GameState INITIALIZED_GAME_STATE = GameState.builder()
            .clientId(CLIENT_UUID)
            .deck(mockDeck)
            .bet(0)
            .isRoundActive(true)
            .natural21(WinnerState.NONE.getValue())

            .player(PlayerHand.builder()
                .hand(new LinkedList<>(List.of(playerCard1, playerCard2)))
                .canSplit(false)
                .bet(10)
                .id("P-001")
                .build())

            .dealerUnmasked(DealerHandUnmasked.builder()
                .hand(new LinkedList<>(List.of(dealerCard1, dealerCard2)))
                .natural21(WinnerState.NONE.getValue())
                .build())

           .dealerMasked(DealerHandMasked.builder()
                .hand(new LinkedList<>(List.of(Card.createMaskedCard(), dealerCard2)))
                .sum(8)
                .canInsure(false)
                .nat21(WinnerState.NONE.getValue())
                .build())
            .build();

        when(gameService.initializeNewRound(any())).thenReturn(INITIALIZED_GAME_STATE);

        mockMvc.perform(post("/api/start_game")
                .session(springMockSession))

            .andExpect(status().isOk())

            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.game_state_hint").value("NEW_ROUND_INITIALIZED"))
            .andExpect(jsonPath("$.current_tokens").value(EXPECTED_TOKENS))

            .andExpect(jsonPath("$.game_state.deckLen").value(EXPECTED_DECK_LEN))
            .andExpect(jsonPath("$.game_state.is_round_active").value(true))
            .andExpect(jsonPath("$.game_state.bet").value(0))

            .andExpect(jsonPath("$.game_state.player.sum").value(11))
            .andExpect(jsonPath("$.game_state.player.canSplit").value(false))
            .andExpect(jsonPath("$.game_state.player.hand[0]").value(EXPECTED_CARD_STRING))

            .andExpect(jsonPath("$.game_state.dealer_masked.sum").value(8))
            .andExpect(jsonPath("$.game_state.dealer_masked.nat_21").value(WinnerState.NONE.getValue()))
            .andExpect(jsonPath("$.game_state.dealer_masked.canInsure").value(false))
            .andExpect(jsonPath("$.game_state.dealer_masked.hand[0]").value(EXPECTED_MASKED_CARD_STRING));

        verify(gameService).initializeNewRound(any());

        GameState savedState = (GameState) springMockSession.getAttribute("gameState");
        Assertions.assertEquals(INITIALIZED_GAME_STATE, savedState);
    }

    @Test
    void playerHit_whenSuccessful() throws Exception {
        Card playerCard1 = new Card(Suit.HEARTS, Rank.FIVE);
        Card playerCard2 = new Card(Suit.CLUBS, Rank.SIX);
        Card hitCard = new Card(Suit.SPADES, Rank.NINE);

        Card dealerVisibleCard = new Card(Suit.CLUBS, Rank.EIGHT);
        final String EXPECTED_MASKED_CARD_STRING = Card.createMaskedCard().toString(); // " ✪"

        final int EXPECTED_PLAYER_SUM = 20; // 5 + 6 + 9
        final int EXPECTED_DEALER_MASKED_SUM = dealerVisibleCard.getBaseValue(); // 8

        Deck mockDeck = new Deck();
        mockDeck.dealCard();

        final int EXPECTED_DECK_LEN = mockDeck.getDeckLength();

       DealerHandMasked explicitDealerMasked = DealerHandMasked.builder()
            .hand(new LinkedList<>(List.of(Card.createMaskedCard(), dealerVisibleCard)))
            .sum(EXPECTED_DEALER_MASKED_SUM)
            .canInsure(false)
            .nat21(WinnerState.NONE.getValue())
            .build();

       GameState UPDATED_GAME_STATE = GameState.builder()
            .clientId(CLIENT_UUID)
            .deck(mockDeck)
            .player(PlayerHand.builder()
                .hand(new LinkedList<>(Arrays.asList(playerCard1, playerCard2, hitCard)))
                .canSplit(false).build())

            .dealerMasked(explicitDealerMasked)
            .dealerUnmasked(DealerHandUnmasked.createEmptyHand())
            .bet(100)
            .betList(new ArrayList<>(List.of(100)))
            .isRoundActive(true)
            .build();

        when(gameService.playerHit(any())).thenReturn(UPDATED_GAME_STATE);

        mockMvc.perform(post("/api/hit")
                .session(springMockSession))

            .andExpect(status().isOk())

            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.game_state_hint").value("HIT_RECIEVED"))
            .andExpect(jsonPath("$.current_tokens").value(EXPECTED_TOKENS))

            .andExpect(jsonPath("$.game_state.deckLen").value(EXPECTED_DECK_LEN))
            .andExpect(jsonPath("$.game_state.is_round_active").value(true))
            .andExpect(jsonPath("$.game_state.bet").value(100))

            .andExpect(jsonPath("$.game_state.player.sum").value(EXPECTED_PLAYER_SUM))
            .andExpect(jsonPath("$.game_state.player.canSplit").value(false))
            .andExpect(jsonPath("$.game_state.player.hand.length()").value(3))
            .andExpect(jsonPath("$.game_state.player.hand[2]").value(hitCard.toString()))

            .andExpect(jsonPath("$.game_state.dealer_masked.sum").value(EXPECTED_DEALER_MASKED_SUM))
            .andExpect(jsonPath("$.game_state.dealer_masked.nat_21").value(WinnerState.NONE.getValue()))
            .andExpect(jsonPath("$.game_state.dealer_masked.canInsure").value(false))
            .andExpect(jsonPath("$.game_state.dealer_masked.hand[0]").value(EXPECTED_MASKED_CARD_STRING)); // Ezt ellenőrizzük!

        verify(gameService).playerHit(any());

        GameState savedState = (GameState) springMockSession.getAttribute("gameState");
        Assertions.assertEquals(UPDATED_GAME_STATE, savedState);
    }
}
