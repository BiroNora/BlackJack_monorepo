package com.blackjack.blackjack.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import jakarta.servlet.http.HttpSession;
import com.blackjack.blackjack.dto.SessionContext;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.service.*;
import com.blackjack.blackjack.exception.GlobalExceptionHandler;

import java.util.Optional;
import java.util.UUID;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;


@ExtendWith(MockitoExtension.class)
public class BetControllerTest {
    private MockMvc mockMvc;

    @InjectMocks private BetController betController;
    @Mock private PlayerService playerService;
    @Mock private GameService gameService;
    @Mock private AuthenticationService authenticationService;
    @Mock private HttpSession mockSession;

    private final UUID CLIENT_UUID = UUID.randomUUID();
    private final Player TEST_PLAYER = new Player(CLIENT_UUID, 1000);
    private final GameState TEST_GAME_STATE = new GameState();

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(betController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void placeBet_shouldReturnOkAndDeductTokens_whenBetIsValid() throws Exception {
        UUID testClientId = TEST_PLAYER.getClientId();
        int BET_AMOUNT = 100;
        int EXPECTED_TOKENS = 900;

        SessionContext mockContext = new SessionContext(
            testClientId,
            TEST_PLAYER,
            mockSession,
            Optional.of(TEST_GAME_STATE)
        );
        Player UPDATED_PLAYER = new Player(CLIENT_UUID, EXPECTED_TOKENS);

        when(authenticationService.validateAndGetContext(any())).thenReturn(mockContext);
        when(playerService.deductBet(any(Player.class), eq(BET_AMOUNT))).thenReturn(UPDATED_PLAYER);
        when(gameService.placeBet(any(), eq(BET_AMOUNT))).thenReturn(TEST_GAME_STATE);

        mockMvc.perform(post("/api/bet")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"bet\": " + BET_AMOUNT + "}"))

            .andExpect(status().isOk());

        verify(playerService).deductBet(TEST_PLAYER, BET_AMOUNT);
    }

    @Test
    void placeBet_shouldReturnBadRequest_whenNotEnoughTokens() throws Exception {
        UUID testClientId = TEST_PLAYER.getClientId();
        int HIGH_BET = 5000;
        String EXCEPTION_MESSAGE = "NOT_ENOUGH_TOKENS_FOR_BET";

        SessionContext mockContext = new SessionContext(
            testClientId,
            TEST_PLAYER,
            mockSession,
            Optional.of(TEST_GAME_STATE)
        );

        when(authenticationService.validateAndGetContext(any(HttpSession.class))).thenReturn(mockContext);

        mockMvc.perform(post("/api/bet")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"bet\": " + HIGH_BET + "}"))

            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value(EXCEPTION_MESSAGE));

        verify(playerService, never()).deductBet(any(), anyInt());
    }
}
