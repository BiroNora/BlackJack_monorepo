package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.SessionContext;
import com.blackjack.blackjack.dto.bet.GameStateBet;
import com.blackjack.blackjack.model.*;
import com.blackjack.blackjack.repository.PlayerRepository;
import com.blackjack.blackjack.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.security.test.context.support.WithMockUser;

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
}
