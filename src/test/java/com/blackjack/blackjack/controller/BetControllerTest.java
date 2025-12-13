package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.SessionContext;
import com.blackjack.blackjack.exception.GlobalExceptionHandler;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.service.AuthenticationService;
import com.blackjack.blackjack.service.GameService;
import com.blackjack.blackjack.service.GameStateService;
import com.blackjack.blackjack.service.PlayerService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@ExtendWith(MockitoExtension.class)
public class BetControllerTest {
    private final UUID CLIENT_UUID = UUID.randomUUID();
    private final Player TEST_PLAYER = new Player(CLIENT_UUID, 1000);
    private final UUID testClientId = TEST_PLAYER.getClientId();
    private final GameState TEST_GAME_STATE = new GameState();
    private MockMvc mockMvc;
    @InjectMocks
    private BetController betController;
    @Mock
    private PlayerService playerService;
    @Mock
    private GameService gameService;
    @Mock
    private GameStateService gameStateService;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private HttpSession mockSession;
    private SessionContext mockContext;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(betController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        this.mockContext = new SessionContext(
            testClientId,
            TEST_PLAYER,
            mockSession,
            Optional.of(TEST_GAME_STATE)
        );

        when(authenticationService.validateAndGetContext(any(HttpSession.class)))
            .thenReturn(this.mockContext);
    }

    @Test
    void placeBet_shouldReturnOkAndDeductTokens_whenBetIsValid() throws Exception {
        int BET_AMOUNT = 100;
        int EXPECTED_TOKENS = 900;

        Player UPDATED_PLAYER = new Player(CLIENT_UUID, EXPECTED_TOKENS);

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
        int HIGH_BET = 5000;
        String EXCEPTION_MESSAGE = "NOT_ENOUGH_TOKENS_FOR_BET";

        mockMvc.perform(post("/api/bet")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"bet\": " + HIGH_BET + "}"))

            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value(EXCEPTION_MESSAGE));

        verify(playerService, never()).deductBet(any(), anyInt());
    }

    @Test
    void retakeBet_shouldReturnOkAndUpdateTokens_whenBetList() throws Exception {
        final int AMOUNT_TO_RETURN_NEGATIVE = -100;
        final int EXPECTED_TOKENS = 1100;

        Player UPDATED_PLAYER = new Player(CLIENT_UUID, EXPECTED_TOKENS);

        when(gameService.retakeBet(any())).thenReturn(AMOUNT_TO_RETURN_NEGATIVE);
        when(playerService.deductBet(any(Player.class), eq(AMOUNT_TO_RETURN_NEGATIVE))).thenReturn(UPDATED_PLAYER);
        when(gameStateService.getGameState(any())).thenReturn(Optional.of(TEST_GAME_STATE));

        mockMvc.perform(post("/api/retake_bet")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        verify(gameService).retakeBet(any());
        verify(playerService).deductBet(any(Player.class), eq(AMOUNT_TO_RETURN_NEGATIVE));
        verify(gameStateService).getGameState(any());
    }

    @Test
    void retakeBet_shouldReturnBadRequest_whenBetListIsEmpty() throws Exception {
        final String EXPECTED_ERROR_CODE = "BAD REQUEST";
        final HttpStatus EXPECTED_STATUS = HttpStatus.BAD_REQUEST;
        final String EXPECTED_MESSAGE = "BAD REQUEST";

        when(gameService.retakeBet(any()))
            .thenThrow(new ResponseStatusException(EXPECTED_STATUS, EXPECTED_MESSAGE));

        mockMvc.perform(post("/api/retake_bet")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON))

            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value(EXPECTED_ERROR_CODE));

        verify(gameService).retakeBet(any());
        verify(playerService, never()).deductBet(any(), anyInt());
        verify(gameStateService, never()).getGameState(any());
    }
}
