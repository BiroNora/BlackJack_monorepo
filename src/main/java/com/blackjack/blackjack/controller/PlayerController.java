package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.init.InitializationRequest;
import com.blackjack.blackjack.dto.init.InitializationResponse;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.repository.PlayerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static com.blackjack.blackjack.common.GameConstants.INITIAL_DECK_LENGTH;
import static com.blackjack.blackjack.common.GameConstants.INITIAL_TOKENS;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class PlayerController {

    private final PlayerRepository playerRepository;

    public PlayerController(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @PostMapping("/initialize_session")
    public ResponseEntity<InitializationResponse> initializeSession(@RequestBody InitializationRequest requestRecord) {

        // 1. Megkeressük a playert az adatbázisban az ID alapján
        UUID rawId = requestRecord.clientId();

        // 2. Itt dől el a végleges érték (egy új, final változóba mentjük)
        final UUID finalClientId = (rawId == null) ? UUID.randomUUID() : rawId;

        // Megkeressük vagy létrehozzuk a playert
        Player player = playerRepository.findByClientId(finalClientId)
            .map(existingPlayer -> {
                // HA MÁR LÉTEZIK: Kinullázzuk az állapotot és az idempotencia kulcsot
                return playerRepository.save(existingPlayer.toBuilder()
                    .currentGameState(null)
                    .idempotencyKey(UUID.randomUUID())
                    .lastActivity(OffsetDateTime.now())
                    .build());
            })
            .orElseGet(() -> {
                // HA ÚJ: Létrehozzuk alapértékekkel
                Player newPlayer = Player.builder()
                    .clientId(finalClientId)
                    .tokens(INITIAL_TOKENS)
                    .currentGameState(null)
                    .lastActivity(OffsetDateTime.now())
                    .idempotencyKey(UUID.randomUUID())
                    .build();
                return playerRepository.save(newPlayer);
            });

        // 2. Összerakjuk a választ PONTOSAN úgy, ahogy kérted
        InitializationResponse.GameStateInit gameStateData =
            InitializationResponse.GameStateInit.builder()
                .deckLen(INITIAL_DECK_LENGTH)
                .build();

        return ResponseEntity.ok(InitializationResponse.builder()
            .status("success")
            .message("success.")
            .gameState(gameStateData)
            .gameStateHint("Place your bet.")
            .tokens(player.getTokens()) // A tényleges token az adatbázisból
            .build());
    }

    @PostMapping("/force_restart")
    public ResponseEntity<?> forceRestart(@RequestBody Map<String, String> body) {
        // A frontend "clientId"-t küld JSON-ben
        String clientIdStr = body.get("clientId");

        if (clientIdStr == null || clientIdStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "clientId is required"));
        }

        try {
            UUID clientId = UUID.fromString(clientIdStr);

            return playerRepository.findByClientId(clientId)
                .map(player -> {
                    // Python: game.restart_game() megfelelője
                    player.setCurrentGameState(null);
                    playerRepository.save(player);

                    return ResponseEntity.ok(Map.of(
                        "status", "success",
                        "current_tokens", player.getTokens(),
                        "game_state", Map.of("status", "BETTING"),
                        "game_state_hint", "HIT_RESTART"
                    ));
                })
                .orElse(ResponseEntity.status(404).body(Map.of("error", "Player not found")));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid UUID format"));
        }
    }
}
