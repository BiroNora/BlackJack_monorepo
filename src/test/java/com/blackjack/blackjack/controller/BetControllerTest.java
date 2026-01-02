package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.bet.BetRequest;
import com.blackjack.blackjack.dto.bet.GameStateBet;
import com.blackjack.blackjack.model.Deck;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.service.BetService;
import com.blackjack.blackjack.service.PlayerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BetController.class)
@AutoConfigureMockMvc(addFilters = false) // Kikapcsoljuk a biztonsági szűrőket a tiszta logika teszteléséhez
public class BetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PlayerService playerService;

    @MockitoBean
    private BetService betService;

    private UUID clientId;
    private UUID idempotencyKey;
    private Player mockPlayer;
    private GameState mockGameState;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        idempotencyKey = UUID.randomUUID();

        mockGameState = GameState.builder()
            .bet(0)
            .betList(List.of())
            .deck(new Deck(List.of())) // Üres pakli a hosszméréshez
            .isRoundActive(false)
            .build();

        mockPlayer = Player.builder()
            .clientId(clientId)
            .tokens(1000)
            .currentGameState(mockGameState)
            .build();
    }

    @Test
    @DisplayName("Sikeres fogadás (placeBet)")
    void placeBet_Success() throws Exception {
        BetRequest request = new BetRequest(clientId, 100, idempotencyKey);
        GameStateBet betDto = new GameStateBet(100, List.of(100), 52);

        when(playerService.getAndValidatePlayer(clientId)).thenReturn(mockPlayer);
        when(playerService.isDuplicateRequest(any(), any())).thenReturn(false);
        when(betService.placeBet(any(), anyInt())).thenReturn(betDto);

        Player savedPlayer = mockPlayer.toBuilder().tokens(900).build();
        when(playerService.deductBet(any(), anyInt())).thenReturn(savedPlayer);

        mockMvc.perform(post("/api/bet")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.current_tokens").value(900))
            .andExpect(jsonPath("$.game_state.bet").value(100));
    }

    @Test
    @DisplayName("Duplikált fogadási kérés esetén a mentett állapotot kapjuk vissza")
    void placeBet_DuplicateRequest() throws Exception {
        BetRequest request = new BetRequest(clientId, 100, idempotencyKey);

        GameState existingState = mockGameState.toBuilder().bet(50).build();
        Player playerWithExistingBet = mockPlayer.toBuilder().currentGameState(existingState).build();

        when(playerService.getAndValidatePlayer(clientId)).thenReturn(playerWithExistingBet);
        when(playerService.isDuplicateRequest(any(), any())).thenReturn(true);

        mockMvc.perform(post("/api/bet")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.game_state.bet").value(50));
    }

    @Test
    @DisplayName("Sikeres tét visszavétel (retakeBet)")
    void retakeBet_Success() throws Exception {
        BetRequest request = new BetRequest(clientId, 0, idempotencyKey);
        GameStateBet emptyBetDto = new GameStateBet(0, List.of(), 52);
        BetService.RetakeResult retakeResult = new BetService.RetakeResult(emptyBetDto, 50);

        when(playerService.getAndValidatePlayer(clientId)).thenReturn(mockPlayer);
        when(playerService.isDuplicateRequest(any(), any())).thenReturn(false);
        when(betService.retakeBet(any())).thenReturn(retakeResult);

        Player savedPlayer = mockPlayer.toBuilder().tokens(1050).build();
        when(playerService.updateTokens(any(), anyInt())).thenReturn(savedPlayer);

        mockMvc.perform(post("/api/retake_bet")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.current_tokens").value(1050))
            .andExpect(jsonPath("$.game_state.bet").value(0));
    }
}
