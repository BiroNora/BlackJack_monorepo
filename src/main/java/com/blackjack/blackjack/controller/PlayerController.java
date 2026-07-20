package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.ApiResponse;
import com.blackjack.blackjack.dto.bet.GameStateBet;
import com.blackjack.blackjack.dto.init.InitializationRequest;
import com.blackjack.blackjack.dto.init.InitializationResponse;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.PhaseState;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.repository.PlayerRepository;
import com.blackjack.blackjack.security.JwtService;
import com.blackjack.blackjack.service.GamePhaseService;
import com.blackjack.blackjack.service.GameStateManager;
import com.blackjack.blackjack.service.PlayerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static com.blackjack.blackjack.common.GameConstants.INITIAL_DECK_LENGTH;
import static com.blackjack.blackjack.common.GameConstants.INITIAL_TOKENS;

@RestController
@RequestMapping("/api")
public class PlayerController {

    private final PlayerRepository playerRepository;
    private final GameStateManager gameStateManager;
    private final PlayerService playerService;
    private final GamePhaseService gamePhaseService;
    private final JwtService jwtService;
    private final ControllerHelper controllerHelper;

    public PlayerController(PlayerRepository playerRepository, GameStateManager gameStateManager, PlayerService playerService, GamePhaseService gamePhaseService, JwtService jwtService, ControllerHelper controllerHelper) {
        this.playerRepository = playerRepository;
        this.gameStateManager = gameStateManager;
        this.playerService = playerService;
        this.gamePhaseService = gamePhaseService;
        this.jwtService = jwtService;
        this.controllerHelper = controllerHelper;
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

        String jwtToken = jwtService.generateToken(String.valueOf(player.getClientId()));

        int refund = gameStateManager.getRefundAmount(player);
        if (refund > 0) {
            player = playerService.updateTokens(player, refund);
            player = gameStateManager.resetBetAndSave(player);
        }

        GameState currState = player.getCurrentGameState();
        if (currState == null) {
            currState = GameState.builder().build();
        }

        GameState updatedState = currState.toBuilder()
            .isSessionInit(true)
            .build();

        player.setCurrentGameState(updatedState);
        playerRepository.save(player);

        GameState calcPhase = gamePhaseService.phaseOfInitialSession(player, currState);

        InitializationResponse.GameStateInit gameStateData =
            InitializationResponse.GameStateInit.builder()
                .deckLen(INITIAL_DECK_LENGTH)
                .targetPhase(calcPhase.getTargetPhase())
                .build();

        return ResponseEntity.ok(InitializationResponse.builder()
            .status("success")
            .message("success.")
            .token(jwtToken)
            .gameState(gameStateData)
            .gameStateHint("INITIALIZING_IS_DONE.")
            .tokens(player.getTokens())
            .totalInitialCards(INITIAL_DECK_LENGTH)
            .build());
    }

    @PostMapping("/recover_game_state")
    public ResponseEntity<?> recoverGameState(@RequestBody Map<String, String> body, Principal principal) {
        UUID clientId = controllerHelper.validateAndGetClientId(body, principal);

        Player player = playerRepository.findByClientId(clientId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PLAYER_NOT_FOUND"));

        GameState currentState = player.getCurrentGameState();

        // Ellenőrizzük, van-e mentett állapota
        if (currentState == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "NO_ACTIVE_SESSION_TO_RECOVER");
        }

        GameState phaseResult = gamePhaseService.phaseOfRecoverGameState(currentState);

        GameState recoveredState = phaseResult.toBuilder()
            .isSessionInit(false)
            .build();

        player.setCurrentGameState(recoveredState);
        playerRepository.save(player);

        boolean isSplit = (recoveredState.getPlayers() != null && !recoveredState.getPlayers().isEmpty())
            || recoveredState.getSplitReq() > 0;

        Object specificGameState = isSplit ?
            gameStateManager.buildGameStateSplitHand(recoveredState) :
            gameStateManager.buildGameStateStart(recoveredState);

        return ResponseEntity.ok(ApiResponse.builder()
            .status("success")
            .currentTokens(player.getTokens())
            .gameState(specificGameState)
            .gameStateHint("RECOVERY_DATA_LOADED")
            .build());
    }

    @PostMapping("/clear_game_state")
    public ResponseEntity<?> clearGameState(@RequestBody Map<String, String> body, Principal principal) {
        UUID clientId = controllerHelper.validateAndGetClientId(body, principal);

        Player player = playerRepository.findByClientId(clientId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PLAYER_NOT_FOUND"));

        GameState currentState = player.getCurrentGameState() != null
            ? player.getCurrentGameState()
            : GameState.builder().build();

        GameState clearedData = gameStateManager.resetRoundState(currentState);
        GameState finalState = gamePhaseService.phaseOfClearGameState(clearedData).toBuilder()
            .isSessionInit(true)
            .bet(0)
            .build();

        player.setCurrentGameState(finalState);
        player.setIdempotencyKey(UUID.randomUUID());
        playerRepository.save(player);

        GameStateBet responseDto = gameStateManager.buildGameStateBet(finalState)
            .toBuilder()
            .deckLen(INITIAL_DECK_LENGTH)
            .build();

        return ResponseEntity.ok(ApiResponse.builder()
            .status("success")
            .currentTokens(player.getTokens())
            .gameState(responseDto)
            .gameStateHint("GAME_STATE_CLEARED")
            .build());
    }

    @PostMapping("/force_restart")
    public ResponseEntity<?> forceRestart(@RequestBody Map<String, String> body, Principal principal) {
        UUID clientId = controllerHelper.validateAndGetClientId(body, principal);

        Player player = playerRepository.findByClientId(clientId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PLAYER_NOT_FOUND"));

        GameState freshState = GameState.builder().build();
        GameState clearedState = gameStateManager.resetRoundState(freshState);

        PhaseState target = PhaseState.BETTING;
        PhaseState pre = PhaseState.SHUFFLING;

        GameState finalState = clearedState.toBuilder()
            .isRoundActive(false)
            .isSessionInit(true)
            .targetPhase(target)
            .prePhase(pre)
            .bet(0)
            .build();

        player.setCurrentGameState(finalState);
        player.setIdempotencyKey(UUID.randomUUID());
        playerRepository.save(player);

        GameStateBet responseDto = gameStateManager.buildGameStateBet(finalState).toBuilder()
            .deckLen(INITIAL_DECK_LENGTH)
            .build();

        return ResponseEntity.ok(ApiResponse.builder()
            .status("success")
            .currentTokens(player.getTokens())
            .gameState(responseDto)
            .gameStateHint("FORCE_RESTART_SUCCESSFUL")
            .build());
    }
}
