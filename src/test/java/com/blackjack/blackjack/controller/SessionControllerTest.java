package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.init.ForceRestartRequest;
import com.blackjack.blackjack.dto.init.InitializationRequest;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.repository.PlayerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static com.blackjack.blackjack.common.GameConstants.INITIAL_DECK_LENGTH;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post; // MockMvc-hez ez kell
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*; // jsonPath, status, cookie innen jön

@WebMvcTest(SessionController.class)
@Import(TestSecurityConfig.class)
public class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private PlayerRepository playerRepository;
    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;
    @MockitoBean
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("initialize_session: Új játékos létrehozása, ha nem küld ID-t")
    void testInitializeSession_NewPlayer() throws Exception {
        InitializationRequest request = new InitializationRequest(null);

        // Szimuláljuk az elmentett playert (mert a kontrollernek kell a visszatérési érték)
        when(playerRepository.save(any(Player.class))).thenAnswer(i -> i.getArgument(0));

        mockMvc.perform(post("/api/initialize_session")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.game_state.deck_len").value(INITIAL_DECK_LENGTH))
            .andExpect(cookie().exists("clientId"))
            .andExpect(request().sessionAttribute("clientId", notNullValue()))
            .andExpect(request().sessionAttribute("gameState", notNullValue()));

        verify(playerRepository, times(1)).save(any(Player.class));
    }

    @Test
    @DisplayName("initialize_session: Meglévő játékos betöltése ID alapján")
    void testInitializeSession_ExistingPlayer() throws Exception {
        UUID existingUuid = UUID.randomUUID();
        InitializationRequest request = new InitializationRequest(existingUuid);

        // Player létrehozása Builder-rel
        Player existingPlayer = Player.builder()
            .id(UUID.randomUUID())
            .clientId(existingUuid)
            .tokens(500)
            .sessionId("old-session-id")
            .idempotencyKey(UUID.randomUUID())
            .build();

        // Szimuláljuk a keresést: megtalálja a DB-ben
        when(playerRepository.findByClientId(existingUuid)).thenReturn(Optional.of(existingPlayer));

        // Szimuláljuk a mentést: a kontroller frissíti a session-t/időt,
        // majd visszakéri az elmentett objektumot.
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/initialize_session")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.tokens").value(500)) // A meglévő zsetonokat várjuk
            .andExpect(cookie().value("clientId", existingUuid.toString()))
            .andExpect(request().sessionAttribute("clientId", existingUuid.toString()));

        verify(playerRepository, times(1)).findByClientId(existingUuid);
        verify(playerRepository, times(1)).save(any(Player.class));
    }

    @Test
    @DisplayName("force_restart: Sikeres állapot alaphelyzetbe állítás")
    void testForceRestart_Success() throws Exception {
        UUID uuid = UUID.randomUUID();

        ForceRestartRequest request = new ForceRestartRequest(uuid.toString());

        Player player = Player.builder()
            .clientId(uuid)
            .tokens(1000)
            .build();

        when(playerRepository.findByClientId(uuid)).thenReturn(Optional.of(player));
        when(playerRepository.save(any(Player.class))).thenAnswer(i -> i.getArgument(0));

        mockMvc.perform(post("/api/force_restart")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))) // Az ObjectMapper a @JsonProperty miatt "client_id"-t fog gyártani belőle
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            // A kontrolleredben: ApiResponse.builder().status("success").gameStateHint("FORCE_RESTART_SUCCESSFUL_RESET_STATE")...
            .andExpect(jsonPath("$.game_state_hint").value("FORCE_RESTART_SUCCESSFUL_RESET_STATE"));

        verify(playerRepository).findByClientId(uuid);
    }

    @Test
    @DisplayName("force_restart: Hiba, ha a játékos nem létezik")
    void testForceRestart_UserNotFound() throws Exception {
        UUID unknownUuid = UUID.randomUUID();
        ForceRestartRequest request = new ForceRestartRequest(unknownUuid.toString());

        when(playerRepository.findByClientId(unknownUuid)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/force_restart")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.game_state_hint").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("force_restart: Hibaüzenet rossz UUID formátum esetén")
    void testForceRestart_InvalidUuidFormat() throws Exception {
        ForceRestartRequest request = new ForceRestartRequest("nem-vagyok-uuid");

        mockMvc.perform(post("/api/force_restart")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest()) // 400-as hiba
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.game_state_hint").value("INVALID_CLIENT_ID_FORMAT"));
    }
}
