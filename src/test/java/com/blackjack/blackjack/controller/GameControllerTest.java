package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.SessionContext;
import com.blackjack.blackjack.model.*;
import com.blackjack.blackjack.repository.PlayerRepository;
import com.blackjack.blackjack.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GameController.class)
@Import(TestSecurityConfig.class)
public class GameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GameService gameService;
    @MockitoBean
    private AuthenticationService authenticationService;
    @MockitoBean
    private PlayerService playerService;
    @MockitoBean
    private GameStateService gameStateService;
    @MockitoBean
    private GameStateManager gameStateManager;
    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory; // Ha kell a sessionhöz
    @MockitoBean
    private PlayerRepository playerRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Player createTestPlayer() {
        return Player.builder()
            .clientId(UUID.randomUUID())
            .tokens(1000)
            .build();
    }

    @Test
    @DisplayName("start_game: Sikeres kör indítás")
    void testStartGame_Success() throws Exception {
        Player player = createTestPlayer();

        UUID uuid = UUID.randomUUID();
        Player mockPlayer = Player.builder()
            .clientId(uuid)
            .tokens(1000)
            .build();

        GameState mockState = GameState.builder()
            .clientId(uuid)
            .build();

        GameState updatedState = GameState.builder()
            .clientId(player.getClientId())
            .isRoundActive(true)
            .bet(100)
            .build();

        // Mockoljuk az autentikációt, ami visszaadja a kontextust
        SessionContext mockContext = new SessionContext(
            uuid,
            mockPlayer,
            null, // A session itt elhagyható, ha a validateAndGetContext-et mockoljuk
            Optional.of(mockState)
        );
        when(authenticationService.validateAndGetContext(any())).thenReturn(mockContext);

        // Mockoljuk a játék indítását
        when(gameService.initializeNewRound(any())).thenReturn(updatedState);

        mockMvc.perform(post("/api/start_game"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.game_state_hint").value("NEW_ROUND_INITIALIZED"))
            .andExpect(jsonPath("$.game_state.is_round_active").value(true));

        verify(gameStateService).saveGameState(updatedState);
    }

    @Test
    @DisplayName("hit: Sikeres kártyahúzás")
    void testPlayerHit_Success() throws Exception {
        UUID uuid = UUID.randomUUID();
        Player player = Player.builder().clientId(uuid).tokens(1000).build();
        GameState currentState = GameState.builder().clientId(uuid).build();
        GameState updatedState = GameState.builder().clientId(uuid).isRoundActive(true).build();

        when(authenticationService.validateAndGetContext(any())).thenReturn(
            new SessionContext(uuid, player, null, Optional.of(currentState))
        );
        when(gameService.playerHit(any())).thenReturn(updatedState);

        mockMvc.perform(post("/api/hit"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.game_state_hint").value("HIT_RECEIVED"));

        verify(gameStateService).saveGameState(updatedState);
    }

    @Test
    @DisplayName("stand_and_rewards: Megállás és nyeremény feldolgozása")
    void testStandAndRewards_Success() throws Exception {
        UUID uuid = UUID.randomUUID();
        Player player = Player.builder().clientId(uuid).tokens(1000).build();
        GameState state = GameState.builder().clientId(uuid).build();

        // A Stand utáni állapot
        GameState stoodState = GameState.builder().clientId(uuid).isRoundActive(false).build();
        // A Reward kalkuláció eredménye (500-as nyeremény)
        GameService.TransactionResult reward = new GameService.TransactionResult(stoodState, 500);

        when(authenticationService.validateAndGetContext(any())).thenReturn(
            new SessionContext(uuid, player, null, Optional.of(state))
        );
        when(gameService.playerStand(any())).thenReturn(stoodState);
        when(gameService.calculateRewards(any())).thenReturn(reward);

        mockMvc.perform(post("/api/stand_and_rewards"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.game_state_hint").value("REWARDS_PROCESSED"));

        verify(playerService).updateTokens(eq(player), eq(500));
        verify(gameStateService).saveGameState(stoodState);
    }

    @Test
    @DisplayName("split_request: Sikeres split és token levonás")
    void testSplitRequest_Success() throws Exception {
        UUID uuid = UUID.randomUUID();
        PlayerHand hand = PlayerHand.builder().bet(200).build();
        Player player = Player.builder().clientId(uuid).tokens(1000).build();
        GameState state = GameState.builder().clientId(uuid).player(hand).build();
        GameState splitState = GameState.builder().clientId(uuid).isRoundActive(true).build();

        when(authenticationService.validateAndGetContext(any())).thenReturn(
            new SessionContext(uuid, player, null, Optional.of(state))
        );
        when(gameService.splitHand(any())).thenReturn(splitState);

        mockMvc.perform(post("/api/split_request"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"));

        // Kritikus: az osztásnál le kell vonni a plusz tétet!
        verify(playerService).deductBet(player, 200);
        verify(gameStateService).saveGameState(splitState);
    }

    @Test
    @DisplayName("double_request: Sikeres duplázás")
    void testDoubleRequest_Success() throws Exception {
        UUID uuid = UUID.randomUUID();
        // 2 lapja van, van elég pénze
        PlayerHand hand = PlayerHand.builder().bet(100).hand(List.of(new Card(Suit.HEARTS, Rank.KING), new Card(Suit.CLUBS, Rank.FIVE))).build();
        Player player = Player.builder().clientId(uuid).tokens(500).build();
        GameState state = GameState.builder().player(hand).build();

        when(authenticationService.validateAndGetContext(any())).thenReturn(new SessionContext(uuid, player, null, Optional.of(state)));
        when(gameService.playerHit(any())).thenReturn(state);
        when(gameService.applyDoubleBet(any(), anyInt())).thenReturn(state);

        mockMvc.perform(post("/api/double_request"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.game_state_hint").value("DOUBLE_DOWN_PROCESSED"));

        verify(playerService).deductBet(eq(player), eq(100));
    }

    @Test
    @DisplayName("double_request: Hiba, ha már több mint 2 lapja van")
    void testDoubleRequest_TooManyCards() throws Exception {
        UUID uuid = UUID.randomUUID();

        Player player = Player.builder()
            .clientId(uuid)
            .tokens(1000)
            .build();

        PlayerHand hand = PlayerHand.builder()
            .bet(100)
            .hand(List.of(
                new Card(Suit.DIAMONDS, Rank.FOUR),
                new Card(Suit.CLUBS, Rank.THREE),
                new Card(Suit.HEARTS, Rank.TWO)))
            .build();

        GameState state = GameState.builder().player(hand).build();

        when(authenticationService.validateAndGetContext(any()))
            .thenReturn(new SessionContext(uuid, player, null, Optional.of(state)));

        mockMvc.perform(post("/api/double_request"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("double_request: Hiba, ha nincs elég token")
    void testDoubleRequest_NotEnoughTokens() throws Exception {
        UUID uuid = UUID.randomUUID();
        Player player = Player.builder().clientId(uuid).tokens(50).build(); // Kevés pénz
        PlayerHand hand = PlayerHand.builder().bet(100).hand(List.of(new Card(Suit.HEARTS, Rank.TEN), new Card(Suit.DIAMONDS, Rank.THREE))).build();
        GameState state = GameState.builder().player(hand).build();

        when(authenticationService.validateAndGetContext(any()))
            .thenReturn(new SessionContext(uuid, player, null, Optional.of(state)));

        mockMvc.perform(post("/api/double_request"))
            .andExpect(status().isBadRequest()); // GameRuleException-t várunk
    }

    @Test
    @DisplayName("ins_request: Sikeres biztosítás kötés")
    void testInsuranceRequest_Success() throws Exception {
        UUID uuid = UUID.randomUUID();
        PlayerHand pHand = PlayerHand.builder().bet(200).build();
        Player player = Player.builder().clientId(uuid).tokens(1000).build();

        DealerHandUnmasked dealerHand = DealerHandUnmasked.builder()
            .hand(List.of(new Card(Suit.SPADES, Rank.ACE), new Card(Suit.HEARTS, Rank.TEN)))
            .build();

        GameState state = GameState.builder()
            .clientId(uuid)
            .bet(200)
            .dealerUnmasked(dealerHand)
            .isRoundActive(true)
            .build();

        GameState updatedState = GameState.builder()
            .clientId(uuid)
            .player(pHand)
            .isRoundActive(true)
            .natural21(3)
            .dealerUnmasked(dealerHand)
            .build();

        GameService.TransactionResult insResult = new GameService.TransactionResult(updatedState, -100);

        when(authenticationService.validateAndGetContext(any())).thenReturn(
            new SessionContext(uuid, player, null, Optional.of(state))
        );
        when(gameService.insuranceRequest(any())).thenReturn(insResult);

        mockMvc.perform(post("/api/ins_request"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.game_state_hint").value("INSURANCE_PROCESSED"));

        verify(playerService).updateTokens(eq(player), eq(-100));
        verify(gameStateService).saveGameState(updatedState);
    }

    @Test
    @DisplayName("set_restart: Játék és tokenek alaphelyzetbe állítása")
    void testSetRestart_Success() throws Exception {
        UUID uuid = UUID.randomUUID();
        // 1. Kiinduló állapot: 0 token és egy létező játék
        Player player = Player.builder().clientId(uuid).tokens(0).build();
        GameState currentState = GameState.builder().clientId(uuid).build();

        // 2. Cél állapot: 1000 token és üres/alaphelyzetbe állított játék
        Player resetPlayer = Player.builder().clientId(uuid).tokens(1000).build();
        GameState resetState = GameState.builder()
            .clientId(uuid)
            .bet(0)
            .isRoundActive(false)
            .build();

        // Mockoljuk az azonosítást
        when(authenticationService.validateAndGetContext(any())).thenReturn(
            new SessionContext(uuid, player, null, Optional.of(currentState))
        );

        // Mockoljuk a reset folyamatot
        when(playerService.resetTokens(any(), eq(1000))).thenReturn(resetPlayer);
        when(gameService.restartGame(any())).thenReturn(resetState);

        mockMvc.perform(post("/api/set_restart"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.current_tokens").value(1000))
            .andExpect(jsonPath("$.game_state_hint").value("GAME_AND_TOKENS_RESET_SUCCESS"));

        verify(playerService).resetTokens(player, 1000);
        verify(gameStateService).saveGameState(resetState);
    }
}
