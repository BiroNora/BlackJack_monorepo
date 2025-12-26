package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.SessionContext;
import com.blackjack.blackjack.exception.GameRuleException;
import com.blackjack.blackjack.model.*;
import com.blackjack.blackjack.repository.PlayerRepository;
import com.blackjack.blackjack.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
    @DisplayName("double_request: Sikeres duplázás")
    void testDoubleRequest_Success() throws Exception {
        UUID uuid = UUID.randomUUID();
        Player player = Player.builder().clientId(uuid).tokens(500).build();
        PlayerHand hand = PlayerHand.builder().bet(100).hand(List.of(new Card(Suit.HEARTS, Rank.KING), new Card(Suit.CLUBS, Rank.FIVE))).build();

        GameState initialGameState = GameState.builder().player(hand).build();
        GameState stateAfterHit = GameState.builder().player(hand).build(); // Service módosítja
        GameState stateAfterDouble = GameState.builder().player(hand).build(); // Második service hívás eredménye

        when(authenticationService.validateAndGetContext(any())).thenReturn(new SessionContext(uuid, player, null, Optional.of(initialGameState)));
        when(gameService.playerHit(initialGameState)).thenReturn(stateAfterHit);
        when(gameService.applyDoubleBet(stateAfterHit, 100)).thenReturn(stateAfterDouble);

        mockMvc.perform(post("/api/double_request").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.game_state_hint").value("DOUBLE_DOWN_PROCESSED"));

        // Ez a sor a kulcs: ellenőrzi, hogy a LÁNC VÉGÉN kapott állapotot mentetted-e el
        verify(gameStateService).saveGameState(stateAfterDouble);
        verify(playerService).deductBet(player, 100);
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

        mockMvc.perform(post("/api/double_request")
                .with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error_code").value("NOT_ENOUGH_TOKENS_FOR_DOUBLE"));
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

        mockMvc.perform(post("/api/double_request")
                .with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error_code").value("DOUBLE_ONLY_ALLOWED_ON_STARTING_HAND"));
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
    @DisplayName("ins_request: Hiba, ha nincs elég token")
    void testInsuranceRequest_NotEnoughTokens() throws Exception {
        UUID uuid = UUID.randomUUID();
        Player player = Player.builder().clientId(uuid).tokens(45).build(); // Kevés pénz
        PlayerHand hand = PlayerHand.builder()
            .bet(100)
            .hand(List.of(new Card(Suit.HEARTS, Rank.TEN), new Card(Suit.DIAMONDS, Rank.THREE))).build();
        GameState state = GameState.builder().player(hand).bet(100).build();

        when(authenticationService.validateAndGetContext(any()))
            .thenReturn(new SessionContext(uuid, player, null, Optional.of(state)));

        mockMvc.perform(post("/api/ins_request")
                .with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error_code").value("NOT_ENOUGH_TOKENS_FOR_INSURANCE"));
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
    @DisplayName("split_request: Hiba, ha nincs elég token")
    void testSplitRequest_NotEnoughTokens() throws Exception {
        UUID uuid = UUID.randomUUID();
        Player player = Player.builder().clientId(uuid).tokens(50).build(); // Kevés pénz
        PlayerHand hand = PlayerHand.builder().bet(100).hand(List.of(new Card(Suit.HEARTS, Rank.TEN), new Card(Suit.DIAMONDS, Rank.TEN))).build();
        GameState state = GameState.builder().player(hand).build();

        when(authenticationService.validateAndGetContext(any()))
            .thenReturn(new SessionContext(uuid, player, null, Optional.of(state)));

        mockMvc.perform(post("/api/split_request")
                .with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error_code").value("NOT_ENOUGH_TOKENS_FOR_SPLIT"));
    }

    @Test
    @DisplayName("add_to_players_list_by_stand: Sikeres kéz mentés listába")
    void testAddToPlayersByStand_Success() throws Exception {
        UUID uuid = UUID.randomUUID();
        Player player = Player.builder().clientId(uuid).tokens(1000).build();

        // Kezdeti állapot
        GameState initialState = GameState.builder().clientId(uuid).build();

        // A Service által visszaadott módosított állapot (pl. már van benne valami a players map-ben)
        GameState updatedState = GameState.builder()
            .clientId(uuid)
            .isRoundActive(true)
            .bet(100)
            .players(java.util.Map.of("hand_1", PlayerHand.builder().bet(100).build()))
            .build();

        when(authenticationService.validateAndGetContext(any()))
            .thenReturn(new SessionContext(uuid, player, null, Optional.of(initialState)));

        when(gameService.addToPlayersListByStand(initialState)).thenReturn(updatedState);

        mockMvc.perform(post("/api/add_to_players_list_by_stand")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.game_state_hint").value("SPLT_REQUEST_PROCESSED"))
            .andExpect(jsonPath("$.game_state.bet").value(100));

        // Ellenőrizzük, hogy az adatbázisba az új, módosított állapot került
        verify(gameStateService).saveGameState(updatedState);
    }

    @Test
    @DisplayName("add_split_player_to_game: Sikeres split játékos aktiválása")
    void testAddSplitPlayerToGame_Success() throws Exception {
        UUID uuid = UUID.randomUUID();
        Player player = Player.builder()
            .clientId(uuid)
            .tokens(1500)
            .build();

        GameState initialGameState = GameState.builder()
            .clientId(uuid)
            .build();

        PlayerHand activeHand = PlayerHand.builder()
            .bet(200)
            .hand(List.of(new Card(Suit.CLUBS, Rank.ACE), new Card(Suit.SPADES, Rank.KING)))
            .build();

        DealerHandMasked dealer = DealerHandMasked.builder()
            .hand(List.of(new Card(Suit.HEARTS, Rank.TEN)))
            .canInsure(false)
            .nat21(0)
            .build();

        Deck mockDeck = Deck.builder()
            .deck(List.of(new Card(Suit.CLUBS, Rank.TWO), new Card(Suit.SPADES, Rank.THREE))) // 2 lap
            .build();

        GameState updatedGameState = GameState.builder()
            .clientId(uuid)
            .player(activeHand)
            .dealerMasked(dealer)
            .deck(mockDeck)
            .aces(true)
            .players(java.util.Map.of("hand_0", PlayerHand.builder().bet(200).build()))
            .splitReq(0)
            .bet(200)
            .isRoundActive(true)
            .build();

        // Mockolás
        when(authenticationService.validateAndGetContext(any()))
            .thenReturn(new SessionContext(uuid, player, null, Optional.of(initialGameState)));

        when(gameService.addSplitPlayerToGame(initialGameState))
            .thenReturn(updatedGameState);

        // 2. Act & Assert - Végrehajtás és Ellenőrzés
        mockMvc.perform(post("/api/add_split_player_to_game")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.game_state_hint").value("SPLT_REQUEST_PROCESSED"))
            .andExpect(jsonPath("$.current_tokens").value(1500))
            // DTO mezők ellenőrzése
            .andExpect(jsonPath("$.game_state.player.bet").value(200))
            .andExpect(jsonPath("$.game_state.aces").value(true))
            .andExpect(jsonPath("$.game_state.deck_len").value(2))
            .andExpect(jsonPath("$.game_state.is_round_active").value(true))
            .andExpect(jsonPath("$.game_state.players").exists());

        // Vérifikáljuk, hogy a mentés megtörtént a Service-től kapott objektummal
        verify(gameStateService).saveGameState(updatedGameState);
    }

    @Test
    @DisplayName("add_player_from_players: Következő játékos aktiválása a listából")
    void testAddPlayerFromPlayers_Success() throws Exception {
        UUID uuid = UUID.randomUUID();
        Player player = Player.builder().clientId(uuid).tokens(2000).build();

        GameState initialState = GameState.builder().clientId(uuid).build();

        DealerHandUnmasked dealerUnmasked = DealerHandUnmasked.builder()
            .hand(List.of(new Card(Suit.HEARTS, Rank.TEN), new Card(Suit.CLUBS, Rank.SEVEN)))
            .natural21(0)
            .sum(17)
            .handState(1)
            .build();

        Deck mockDeck = Deck.builder()
            .deck(new java.util.ArrayList<>(Collections.nCopies(10, new Card(Suit.SPADES, Rank.TWO))))
            .build();

        GameState updatedState = GameState.builder()
            .clientId(uuid)
            .player(PlayerHand.builder().bet(100).build())
            .dealerUnmasked(dealerUnmasked)
            .deck(mockDeck)
            .aces(false)
            .players(java.util.Map.of("hand_1", PlayerHand.builder().build()))
            .isRoundActive(true)
            .bet(100)
            .build();

        when(authenticationService.validateAndGetContext(any())).thenReturn(
            new SessionContext(uuid, player, null, Optional.of(initialState))
        );

        when(gameService.addPlayerFromPlayers(initialState)).thenReturn(updatedState);

        mockMvc.perform(post("/api/add_player_from_players")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.game_state.deck_len").value(10))
            .andExpect(jsonPath("$.game_state.dealer_unmasked.hand[0]").value("♥10"))
            .andExpect(jsonPath("$.game_state.dealer_unmasked.sum").value(17))
            .andExpect(jsonPath("$.game_state_hint").value("SPLT_REQUEST_PROCESSED"));

        verify(gameStateService).saveGameState(updatedState);
    }

    @Test
    @DisplayName("split_hit: Sikeres kártyahúzás splitelt kézre")
    void testSplitHit_Success() throws Exception {
        UUID uuid = UUID.randomUUID();
        Player player = Player.builder().clientId(uuid).tokens(1000).build();

        GameState initialState = GameState.builder().clientId(uuid).build();

        Card newCard = new Card(Suit.SPADES, Rank.ACE);
        Deck updatedDeck = Deck.builder()
            .deck(new java.util.ArrayList<>(Collections.nCopies(47, new Card(Suit.HEARTS, Rank.TWO))))
            .build();

        PlayerHand updatedHand = PlayerHand.builder()
            .bet(200)
            .hand(List.of(newCard))
            .sum(11)
            .build();

        GameState updatedState = GameState.builder()
            .clientId(uuid)
            .player(updatedHand)
            .deck(updatedDeck)
            .isRoundActive(true)
            .build();

        when(authenticationService.validateAndGetContext(any())).thenReturn(
            new SessionContext(uuid, player, null, Optional.of(initialState))
        );

        when(gameService.playerHit(initialState)).thenReturn(updatedState);

        mockMvc.perform(post("/api/split_hit")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.game_state_hint").value("SPLT_HIT_REQUEST_PROCESSED"))
            // Kártya ellenőrzése Stringként (a JSON kimenet alapján)
            .andExpect(jsonPath("$.game_state.player.hand[0]").value("♠A"))
            .andExpect(jsonPath("$.game_state.deck_len").value(47));

        verify(gameStateService).saveGameState(updatedState);
    }

    @Test
    @DisplayName("split_stand_and_rewards: Sikeres megállás és kifizetés")
    void testSplitStandAndRewards_Success() throws Exception {
        UUID uuid = UUID.randomUUID();
        Player player = Player.builder().clientId(uuid).tokens(1000).build();
        GameState initialState = GameState.builder().clientId(uuid).build();

        DealerHandUnmasked dealerUnmasked = DealerHandUnmasked.builder()
            .hand(List.of(new Card(Suit.HEARTS, Rank.KING), new Card(Suit.DIAMONDS, Rank.SEVEN)))
            .sum(17)
            .build();

        GameState stoodState = GameState.builder().clientId(uuid).build();

        GameState rewardedState = GameState.builder()
            .clientId(uuid)
            .player(PlayerHand.builder().bet(200).build())
            .dealerUnmasked(dealerUnmasked)
            .deck(Deck.builder().deck(List.of(new Card(Suit.CLUBS, Rank.TWO))).build())
            .winner(1) // Játékos nyert
            .isRoundActive(false)
            .build();

        GameService.TransactionResult mockResult = new GameService.TransactionResult(rewardedState, 400);

        when(authenticationService.validateAndGetContext(any())).thenReturn(
            new SessionContext(uuid, player, null, Optional.of(initialState))
        );

        when(gameService.playerStand(initialState)).thenReturn(stoodState);
        when(gameService.calculateRewards(stoodState)).thenReturn(mockResult);

        mockMvc.perform(post("/api/split_stand_and_rewards")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.game_state_hint").value("REWARDS_PROCESSED"))
            .andExpect(jsonPath("$.game_state.winner").value(1))
            .andExpect(jsonPath("$.game_state.dealer_unmasked.hand[0]").value("♥K"));

        // Kritikus: ellenőrizzük, hogy a tokenek frissítése és a mentés megtörtént-e
        verify(playerService).updateTokens(player, 400);
        verify(gameStateService).saveGameState(rewardedState);
    }

    @Test
    @DisplayName("split_double_request: Sikeres duplázás és zsetonlevonás")
    void testSplitDoubleRequest_Success() throws Exception {
        UUID uuid = UUID.randomUUID();
        Player player = Player.builder().clientId(uuid).tokens(1000).build();

        PlayerHand initialHand = PlayerHand.builder().bet(200).build();
        GameState initialState = GameState.builder()
            .clientId(uuid)
            .player(initialHand)
            .build();

        GameState stateAfterHit = GameState.builder().clientId(uuid).build();

        GameState finalState = GameState.builder()
            .clientId(uuid)
            .player(PlayerHand.builder().bet(400).build())
            .deck(Deck.builder().deck(List.of(new Card(Suit.SPADES, Rank.ACE))).build())
            .bet(400)
            .isRoundActive(true)
            .build();

        when(authenticationService.validateAndGetContext(any())).thenReturn(
            new SessionContext(uuid, player, null, Optional.of(initialState))
        );

        when(gameService.playerHit(initialState)).thenReturn(stateAfterHit);
        when(gameService.applyDoubleBet(stateAfterHit, 200)).thenReturn(finalState);

        mockMvc.perform(post("/api/split_double_request")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.game_state.bet").value(400))
            .andExpect(jsonPath("$.game_state.deck_len").value(1))
            .andExpect(jsonPath("$.game_state_hint").value("SPLT_REQUEST_PROCESSED"));

        verify(playerService).deductBet(player, 200);
        verify(gameStateService).saveGameState(finalState);
    }

    @Test
    @DisplayName("split_double_request: Hiba - Nincs elég zseton a duplázáshoz")
    void testSplitDoubleRequest_NotEnoughTokens() throws Exception {
        UUID uuid = UUID.randomUUID();
        Player player = Player.builder().clientId(uuid).tokens(100).build();
        PlayerHand hand = PlayerHand.builder().bet(200).build();
        GameState state = GameState.builder().player(hand).build();

        when(authenticationService.validateAndGetContext(any())).thenReturn(
            new SessionContext(uuid, player, null, Optional.of(state))
        );

        mockMvc.perform(post("/api/split_double_request")
                .with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(result -> assertInstanceOf(GameRuleException.class, result.getResolvedException()))
            .andExpect(jsonPath("$.error_code").value("NOT_ENOUGH_TOKENS_FOR_DOUBLE"));

        verifyNoInteractions(gameService);
        verify(playerService, never()).deductBet(any(), anyInt());
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
