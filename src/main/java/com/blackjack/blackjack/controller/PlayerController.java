package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.init.InitializationRequest;
import com.blackjack.blackjack.dto.init.InitializationResponse;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.PhaseState;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.repository.PlayerRepository;
import com.blackjack.blackjack.service.GamePhaseService;
import com.blackjack.blackjack.service.GameStateManager;
import com.blackjack.blackjack.service.PlayerService;
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
    private final GameStateManager gameStateManager;
    private final PlayerService playerService;
    private final GamePhaseService gamePhaseService;

    public PlayerController(PlayerRepository playerRepository, GameStateManager gameStateManager, PlayerService playerService, GamePhaseService gamePhaseService) {
        this.playerRepository = playerRepository;
        this.gameStateManager = gameStateManager;
        this.playerService = playerService;
        this.gamePhaseService = gamePhaseService;
    }

    @PostMapping("/initialize_session")
    public ResponseEntity<InitializationResponse> initializeSession(@RequestBody InitializationRequest requestRecord) {

        // 1. Megkeressük a playert az adatbázisban az ID alapján
        UUID rawId = requestRecord.clientId();
        final UUID finalClientId = (rawId == null) ? UUID.randomUUID() : rawId;

        Player player = playerRepository.findByClientId(finalClientId)
            .orElseGet(() -> playerRepository.save(Player.builder()
                .clientId(finalClientId)
                .tokens(INITIAL_TOKENS)
                .lastActivity(OffsetDateTime.now())
                .idempotencyKey(UUID.randomUUID())
                .build()));

        int refund = gameStateManager.getRefundAmount(player);
        if (refund > 0) {
            player = playerService.updateTokens(player, refund);
            player = gameStateManager.resetBetAndSave(player);
        }

        GameState currState = player.getCurrentGameState();
        if (currState == null) {
            currState = GameState.builder().build();
        }

        PhaseState targetPhase = gamePhaseService.calculateInitialPhase(player, currState);
        
        int currDeckSize = (currState.getDeck() != null) ? currState.getDeck().getDeckLength() : INITIAL_DECK_LENGTH;

        InitializationResponse.GameStateInit gameStateData =
            InitializationResponse.GameStateInit.builder()
                .deckLen(currDeckSize)
                .targetPhase(targetPhase)
                .build();

        return ResponseEntity.ok(InitializationResponse.builder()
            .status("success")
            .message("success.")
            .gameState(gameStateData)
            .gameStateHint("INITIALIZING_IS_DONE.")
            .tokens(player.getTokens())
            .totalInitialCards(INITIAL_DECK_LENGTH)
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
