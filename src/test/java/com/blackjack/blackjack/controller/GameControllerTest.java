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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.hamcrest.Matchers.is;

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
    @Mock private SessionContext mockContext;
    @Mock private GameService gameService;
    @Mock private PlayerService playerService;
    @Mock private GameStateService gameStateService;
    @Mock private PlayerRepository playerRepository;
    private MockHttpSession springMockSession;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        // 1. MockMvc inicializálása
        this.mockMvc = MockMvcBuilders.standaloneSetup(gameController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        // 2. Spring-specifikus session létrehozása
        this.springMockSession = new MockHttpSession();
        this.springMockSession.setAttribute("gameState", MOCKED_INITIAL_GAME_STATE);

        // 3. Beállítjuk a mockContext alapértelmezett viselkedését, mielőtt a controller hívná.
        Mockito.lenient().when(this.mockContext.player()).thenReturn(TEST_PLAYER);
        Mockito.lenient().when(this.mockContext.gameStateOptional()).thenReturn(Optional.of(MOCKED_INITIAL_GAME_STATE));
        Mockito.lenient().when(this.mockContext.clientId()).thenReturn(testClientId);
        Mockito.lenient().when(this.mockContext.session()).thenReturn(springMockSession);

        // 4. AuthenticationService beállítása
        // Ez a hívás adja vissza a BEÁLLÍTOTT MOCK-ot a controllernek.
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


    @Test
    void standAndRewards_whenPlayerWins_thenRewardsProcessed() throws Exception {
        // --- ARRANGE: ÁLLANDÓK DEFINIÁLÁSA ---
        final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
        final UUID CLIENT_UUID = UUID.fromString("00000000-0000-0000-0000-000000000002");

        final int INITIAL_TOKENS = 1000;
        final int PLAYER_BET = 100;
        final int EXPECTED_REWARD = 200;
        final int EXPECTED_FINAL_TOKENS = INITIAL_TOKENS + EXPECTED_REWARD;

        // Kártyák és Sum változók
        Card playerCard1 = new Card(Suit.HEARTS, Rank.TEN);
        Card playerCard2 = new Card(Suit.DIAMONDS, Rank.TEN);
        final int PLAYER_SUM = 20;

        // Dealer lapok
        Card dealerCard1 = new Card(Suit.SPADES, Rank.TEN);
        Card dealerCard2 = new Card(Suit.CLUBS, Rank.SIX);
        Card dealerHitCard = new Card(Suit.HEARTS, Rank.ACE);
        final int FINAL_DEALER_SUM = 17;

        // Deck mérete
        Deck finalDeck = new Deck();
        finalDeck.dealCard();
        final int EXPECTED_DECK_LEN = finalDeck.getDeckLength();

        // 1. Initial Player beállítása
        Player initialPlayer = new Player(INITIAL_TOKENS);
        initialPlayer.setId(PLAYER_ID);
        initialPlayer.setClientId(CLIENT_UUID);
        initialPlayer.setSessionId("mock-session-id");
        initialPlayer.setIdempotencyKey(UUID.randomUUID());
        when(this.mockContext.player()).thenReturn(initialPlayer);

        // 2. Kezdeti GameState/PlayerHand létrehozása a mockhoz (initialGameState)
        GameState initialGameState = GameState.builder()
            .clientId(CLIENT_UUID)
            .deck(new Deck())
            .bet(PLAYER_BET)
            .isRoundActive(true)
            .player(PlayerHand.builder()
                .id(CLIENT_UUID.toString())
                .hand(new LinkedList<>(List.of(playerCard1, playerCard2)))
                .bet(PLAYER_BET)
                .canSplit(false)
                .stated(false)
                .build())
            .dealerUnmasked(DealerHandUnmasked.createEmptyHand())
            .build();

        // 3. Mockolt VÉGSŐ GameState létrehozása (playerStand visszatérése)
        GameState STOOD_GAME_STATE = initialGameState.toBuilder()
            .deck(finalDeck)
            .isRoundActive(true)
            .winner(WinnerState.PLAYER_WON.getValue())
            .bet(PLAYER_BET)
            // PlayerHand újraépítése a kezdeti kártyákkal
            .player(PlayerHand.builder()
                .id(CLIENT_UUID.toString())
                .hand(initialGameState.getPlayer().getHand())
                .bet(PLAYER_BET)
                .canSplit(initialGameState.getPlayer().isCanSplit())
                .stated(initialGameState.getPlayer().isStated())
                .build())
            .dealerUnmasked(DealerHandUnmasked.builder()
                .hand(new LinkedList<>(Arrays.asList(dealerCard1, dealerCard2, dealerHitCard)))
                .build())
            .build();

        // Mockoljuk a GameService.playerStand hívását
        // Használjuk az eq()-t a Mockito-konzisztencia miatt, ha a Controller any()-val hívná is, de mi pontosságot várunk.
        when(gameService.playerStand(eq(Optional.of(initialGameState)))).thenReturn(STOOD_GAME_STATE);

        // 4. Rewards számítás utáni állapot (calculateRewards visszatérése)
        // KRITIKUS JAVÍTÁS (NPE ellen): PlayerHand újraépítése a builderrel, MEGTARTVA a KÁRTYA LISTÁT!
        GameState REWARDED_GAME_STATE = STOOD_GAME_STATE.builder()
            .bet(0)
            .isRoundActive(false)
            .player(PlayerHand.builder()
                .id(CLIENT_UUID.toString())
                .hand(STOOD_GAME_STATE.getPlayer().getHand()) // Kártyák átvétele
                .bet(0) // Nullázza a fogadást
                .canSplit(STOOD_GAME_STATE.getPlayer().isCanSplit())
                .stated(STOOD_GAME_STATE.getPlayer().isStated())
                .build())
            .build();

        GameService.TransactionResult rewardResult = new GameService.TransactionResult(REWARDED_GAME_STATE, EXPECTED_REWARD);
        when(gameService.calculateRewards(eq(STOOD_GAME_STATE))).thenReturn(rewardResult);

        // 5. Frissített Játékos objektum létrehozása ÉS Tokens frissítésének mockolása
        Player expectedPlayerAfterUpdate = new Player(EXPECTED_FINAL_TOKENS);
        expectedPlayerAfterUpdate.setId(PLAYER_ID);
        expectedPlayerAfterUpdate.setClientId(CLIENT_UUID);
        expectedPlayerAfterUpdate.setSessionId("mock-session-id");
        expectedPlayerAfterUpdate.setIdempotencyKey(UUID.randomUUID());

        // Mockoljuk a PlayerService.updateTokens hívását
        when(playerService.updateTokens(any(Player.class), eq(EXPECTED_REWARD)))
            .thenReturn(expectedPlayerAfterUpdate);

        // 6. SessionContext mockolása:
        Mockito.lenient().when(this.mockContext.player()).thenReturn(expectedPlayerAfterUpdate);


        // --- ACT & ASSERT ---
        mockMvc.perform(post("/api/stand_and_rewards")
                .session(springMockSession))

            .andExpect(status().isOk())

            // JSON válasz ellenőrzései:
            // JAVÍTÁS: Sima String összehasonlítás. (Ezt tette tönkre az is() a legutóbbi futásnál)
            .andExpect(jsonPath("$.status").value("success"))

            // JAVÍTÁS: Itt KELL az is(String) a típuskonverziós hiba elkerülésére (ami a legelső hiba volt).
            .andExpect(jsonPath("$.game_state_hint").value(is("REWARDS_PROCESSED")))

            .andExpect(jsonPath("$.current_tokens").value(EXPECTED_FINAL_TOKENS))

            // JÁTÉKOS KÉZ ELLENŐRZÉSE
            .andExpect(jsonPath("$.gameState.player.sum").value(PLAYER_SUM))
            .andExpect(jsonPath("$.gameState.player.bet").value(0))
            .andExpect(jsonPath("$.gameState.player.hand.length()").value(2))

            // Dealer UNMASKED kéz ellenőrzése
            .andExpect(jsonPath("$.gameState.dealer_unmasked.sum").value(FINAL_DEALER_SUM))
            .andExpect(jsonPath("$.gameState.dealer_unmasked.hand.length()").value(3));


        // --- VERIFY ---

        // Ellenőrizze, hogy a GameService meghívásra került a "playerStand" metódussal
        verify(gameService).playerStand(eq(Optional.of(initialGameState)));

        // Ellenőrizze, hogy a GameService meghívásra került a "calculateRewards" metódussal
        verify(gameService).calculateRewards(eq(STOOD_GAME_STATE));

        // Ellenőrizze, hogy a PlayerService meghívásra került a "updateTokens" metódussal
        verify(playerService).updateTokens(any(Player.class), eq(EXPECTED_REWARD));

        // Ellenőrizze, hogy a GameStateService meghívásra került a "saveGameState" metódussal
        verify(gameStateService).saveGameState(eq(REWARDED_GAME_STATE));

        // Ellenőrizze a GameState-et a session-ben
        GameState savedState = (GameState) springMockSession.getAttribute("gameState");
        Assertions.assertEquals(REWARDED_GAME_STATE, savedState,
            "A GameState MODELLT megfelelően be kell állítani a session attribútumban a jutalmazás utáni állapottal.");
    }
}
