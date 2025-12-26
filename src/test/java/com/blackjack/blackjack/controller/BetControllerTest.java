package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.SessionContext;
import com.blackjack.blackjack.dto.bet.GameStateBet;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BetController.class)
class BetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GameStateService gameStateService;

    @MockitoBean
    private PlayerService playerService;

    @MockitoBean
    private GameService gameService;

    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private BetService betService;

    private UUID uuid;
    private Player player;

    @BeforeEach
    void setUp() {
        uuid = UUID.randomUUID();
        player = Player.builder().clientId(uuid).tokens(1000).build();
    }

    @Test
    @WithMockUser // <--- Ez szimulálja a bejelentkezett felhasználót (401 hiba ellen)
    @DisplayName("placeBet: Sikeres tétrakás")
    void testPlaceBet_Success() throws Exception {
        GameState state = GameState.builder().clientId(uuid).build();
        GameStateBet betDto = GameStateBet.builder().bet(200).build();
        Player updatedPlayer = Player.builder().clientId(uuid).tokens(800).build();

        when(authenticationService.validateAndGetContext(any())).thenReturn(
            new SessionContext(uuid, player, null, Optional.of(state))
        );
        when(betService.placeBet(any(), eq(200))).thenReturn(betDto);
        when(playerService.deductBet(any(), eq(200))).thenReturn(updatedPlayer);

        mockMvc.perform(post("/api/bet")
                .with(csrf()) // CSRF védelem megkerülése
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bet\": 200}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.current_tokens").value(800));

        verify(gameStateService).saveGameState(any(GameState.class));
    }

    @Test
    @WithMockUser
    @DisplayName("retakeBet: Sikeres tét visszavétel")
    void testRetakeBet_Success() throws Exception {
        GameState state = GameState.builder().clientId(uuid).bet(200).build();
        GameStateBet betDto = GameStateBet.builder().bet(0).betList(List.of()).build();
        BetService.RetakeResult result = new BetService.RetakeResult(betDto, 200);
        Player updatedPlayer = Player.builder().clientId(uuid).tokens(1000).build();

        when(authenticationService.validateAndGetContext(any())).thenReturn(
            new SessionContext(uuid, player, null, Optional.of(state))
        );
        when(betService.retakeBet(any())).thenReturn(result);
        // Figyelem: a negatív deductBet jelenti a visszakapást a kódban (- -200 = +200)
        when(playerService.deductBet(any(), eq(-200))).thenReturn(updatedPlayer);

        mockMvc.perform(post("/api/retake_bet")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.current_tokens").value(1000));

        verify(gameStateService).saveGameState(any(GameState.class));
    }

    @Test
    @WithMockUser
    @DisplayName("placeBet: Hiba, ha nincs játékállapot")
    void testPlaceBet_NoGameState() throws Exception {
        // Arrange: Üres Optional-t adunk vissza a context-ben
        when(authenticationService.validateAndGetContext(any())).thenReturn(
            new SessionContext(uuid, player, null, Optional.empty()) // <--- Nincs state!
        );

        mockMvc.perform(post("/api/bet")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bet\": 200}"))
            .andExpect(status().isInternalServerError()); // 500-as hibát várunk az orElseThrow miatt
    }

    @Test
    @WithMockUser
    @DisplayName("placeBet: Hiba, ha a tét túl alacsony")
    void testPlaceBet_TooLow() throws Exception {
        // 1. Arrange: Először a context-et kell beállítani, mert a kontroller ezzel kezd
        GameState state = GameState.builder()
            .clientId(uuid)
            .isRoundActive(false)
            .build();

        when(authenticationService.validateAndGetContext(any())).thenReturn(
            new SessionContext(uuid, player, null, Optional.of(state))
        );

        // 2. Szimuláljuk, hogy a service dobja a ResponseStatusException-t
        when(betService.placeBet(any(), anyInt()))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "BET_TOO_LOW"));

        // 3. Act & Assert: Végezzük el a hívást és ellenőrizzük a GlobalExceptionHandler válaszát
        mockMvc.perform(post("/api/bet")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bet\": 1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error_code").value("BET_TOO_LOW"));
    }

    @Test
    @WithMockUser
    @DisplayName("retakeBet: Hiba, ha nincs visszavehető tét")
    void testRetakeBet_NoBet() throws Exception {
        GameState state = GameState.builder().clientId(uuid).betList(List.of()).build();

        when(authenticationService.validateAndGetContext(any())).thenReturn(
            new SessionContext(uuid, player, null, Optional.of(state))
        );

        // Szimuláljuk, hogy a service hibát dob, mert nincs mit visszavenni
        when(betService.retakeBet(any()))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "NO_BET_TO_RETAKE"));

        mockMvc.perform(post("/api/retake_bet")
                .with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error_code").value("NO_BET_TO_RETAKE"));
    }
}
