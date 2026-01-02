package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.ActionRequest;
import com.blackjack.blackjack.model.Card;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.model.PlayerHand;
import com.blackjack.blackjack.service.GameService;
import com.blackjack.blackjack.service.GameStateManager;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GameController.class)
@AutoConfigureMockMvc(addFilters = false) // Security kikapcsolása a logikai teszthez
public class GameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GameService gameService;
    @MockitoBean
    private PlayerService playerService;
    @MockitoBean
    private GameStateManager gameStateManager;

    private UUID clientId;
    private UUID idempotencyKey;
    private Player mockPlayer;
    private GameState mockGameState;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        idempotencyKey = UUID.randomUUID();

        mockGameState = GameState.builder()
            .bet(100)
            .isRoundActive(true)
            .build();

        mockPlayer = Player.builder()
            .clientId(clientId)
            .tokens(1000)
            .currentGameState(mockGameState)
            .build();
    }

    // --- KERETRENDSZER (handleAction) TESZTEK ---

    @Test
    @DisplayName("handleAction: Idempotencia - Ha a kulcs már létezik, nem fut le újra a logika")
    void handleAction_Idempotency() throws Exception {
        ActionRequest req = new ActionRequest();
        req.setClientId(clientId);
        req.setIdempotencyKey(idempotencyKey);

        when(playerService.getAndValidatePlayer(clientId)).thenReturn(mockPlayer);
        when(playerService.isDuplicateRequest(any(Player.class), eq(idempotencyKey))).thenReturn(true);

        mockMvc.perform(post("/api/hit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.current_tokens").value(1000));

        // Ellenőrizzük, hogy a játék logika (hit) NEM futott le
        verifyNoInteractions(gameService);
        // Ellenőrizzük, hogy mentés sem történt, mert csak visszaadtuk a régit
        verify(playerService, never()).savePlayer(any());
    }

    @Test
    @DisplayName("handleAction: Sima mentés - Ha tokenChange null, a savePlayer hívódik meg")
    void handleAction_SimpleSave() throws Exception {
        ActionRequest req = new ActionRequest();
        req.setClientId(clientId);
        req.setIdempotencyKey(idempotencyKey);

        when(playerService.getAndValidatePlayer(clientId)).thenReturn(mockPlayer);
        when(playerService.isDuplicateRequest(any(), any())).thenReturn(false);
        when(gameService.playerHit(any())).thenReturn(mockGameState);
        when(playerService.savePlayer(any())).thenReturn(mockPlayer);

        mockMvc.perform(post("/api/hit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk());

        // Mivel a HIT nem változtat tokent, a sima savePlayer-nek kell futnia
        verify(playerService).savePlayer(any());
        verify(playerService, never()).updateTokens(any(), anyInt());
    }

    @Test
    @DisplayName("handleAction: Atomi mentés - Ha van tokenChange, az updateTokens hívódik meg")
    void handleAction_TokenUpdate() throws Exception {
        ActionRequest req = new ActionRequest();
        req.setClientId(clientId);
        req.setIdempotencyKey(idempotencyKey);

        PlayerHand mockPlayerHand = mock(PlayerHand.class);

        Card card1 = mock(Card.class);
        Card card2 = mock(Card.class);
        List<Card> cards = List.of(card1, card2);

        when(mockPlayerHand.hand()).thenReturn(cards);

        GameState stateWithHand = mockGameState.toBuilder()
            .player(mockPlayerHand)
            .build();

        Player playerWithHand = mockPlayer.toBuilder()
            .currentGameState(stateWithHand)
            .build();

        when(playerService.getAndValidatePlayer(clientId)).thenReturn(playerWithHand);
        when(gameService.playerHit(any())).thenReturn(stateWithHand);
        when(gameService.applyDoubleBet(any(), anyInt())).thenReturn(stateWithHand);
        when(playerService.updateTokens(any(), anyInt())).thenReturn(playerWithHand);

        mockMvc.perform(post("/api/double_request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk());

        // Ellenőrizzük az updateTokens hívást a negatív bet-tel (-100)
        verify(playerService).updateTokens(any(), eq(-100));
        verify(playerService, never()).savePlayer(any());
    }

    // --- ÜZLETI SZABÁLY TESZTEK ---

    @Test
    @DisplayName("Double: Hiba, ha a játékosnak nincs elég pénze")
    void doubleRequest_InsufficientTokens() throws Exception {
        ActionRequest req = new ActionRequest();
        req.setClientId(clientId);
        req.setIdempotencyKey(idempotencyKey);

        PlayerHand mockPlayerHand = mock(PlayerHand.class);
        // Valódi listát használunk, hogy elkerüljük a láncolt mockolási hibát
        when(mockPlayerHand.hand()).thenReturn(List.of(mock(Card.class), mock(Card.class)));

        GameState stateWithHand = mockGameState.toBuilder()
            .player(mockPlayerHand)
            .bet(100)
            .isRoundActive(true)
            .build();

        Player poorPlayer = mockPlayer.toBuilder()
            .tokens(50)
            .currentGameState(stateWithHand)
            .build();

        when(playerService.getAndValidatePlayer(clientId)).thenReturn(poorPlayer);

        mockMvc.perform(post("/api/double_request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest()); // GameRuleException -> 400

        // Ellenőrizzük, hogy a hiba miatt semmilyen mentés nem történt
        verify(playerService, never()).savePlayer(any());
        verify(playerService, never()).updateTokens(any(), anyInt());
    }
}
