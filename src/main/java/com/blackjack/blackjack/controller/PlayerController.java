package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.ApiResponse;
import com.blackjack.blackjack.dto.bet.GameStateBet;
import com.blackjack.blackjack.dto.init.InitializationRequest;
import com.blackjack.blackjack.dto.init.InitializationResponse;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.PhaseState;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.repository.PlayerRepository;
import com.blackjack.blackjack.service.GamePhaseService;
import com.blackjack.blackjack.service.GameStateManager;
import com.blackjack.blackjack.service.PlayerService;
import org.springframework.http.HttpStatus;
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

        GamePhaseService.DeckPhaseResult phase = gamePhaseService.phaseOfInitialSession(player, currState);

        GameState updatedState = currState.toBuilder()
            .isSessionInit(true)
            .prePhase(PhaseState.NONE)
            .build();

        player.setCurrentGameState(updatedState);
        playerRepository.save(player);

        InitializationResponse.GameStateInit gameStateData =
            InitializationResponse.GameStateInit.builder()
                .deckLen(phase.deckLen())
                .targetPhase(phase.targetPhase())
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
        String clientIdStr = body.get("clientId");

        if (clientIdStr == null || clientIdStr.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                .status("error")
                .gameStateHint("CLIENT_ID_REQUIRED")
                .build());
        }

        try {
            UUID clientId = UUID.fromString(clientIdStr);

            return playerRepository.findByClientId(clientId)
                .map(player -> {
                    PhaseState target = PhaseState.BETTING;
                    PhaseState pre = PhaseState.SHUFFLING;

                    GameState emptyState = GameState.builder()
                        .isRoundActive(false)
                        .isSessionInit(true)
                        .targetPhase(target)
                        .prePhase(pre)
                        .build();

                    player.setCurrentGameState(emptyState);
                    player.setIdempotencyKey(null);
                    playerRepository.save(player);

                    return ResponseEntity.ok(ApiResponse.builder()
                        .status("success")
                        .currentTokens(player.getTokens())
                        .gameState(GameStateBet.builder()
                            .deckLen(INITIAL_DECK_LENGTH)
                            .targetPhase(target)
                            .prePhase(pre)
                            .build())
                        .gameStateHint("FORCE_RESTART_SUCCESSFUL")
                        .build());
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.builder()
                    .status("error")
                    .gameStateHint("PLAYER_NOT_FOUND")
                    .build()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                .status("error")
                .gameStateHint("INVALID_UUID_FORMAT")
                .build());
        }
    }
}
