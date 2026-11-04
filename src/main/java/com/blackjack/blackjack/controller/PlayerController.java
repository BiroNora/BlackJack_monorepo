package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.ApiResponse;
import com.blackjack.blackjack.dto.bet.GameStateBet;
import com.blackjack.blackjack.dto.init.ConflictResponse;
import com.blackjack.blackjack.dto.init.InitializationRequest;
import com.blackjack.blackjack.dto.init.InitializationResponse;
import com.blackjack.blackjack.dto.init.InitializationResponse.GameStateInit;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.PhaseState;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.repository.PlayerRepository;
import com.blackjack.blackjack.security.JwtService;
import com.blackjack.blackjack.service.GamePhaseService;
import com.blackjack.blackjack.service.GameStateManager;
import com.blackjack.blackjack.service.PlayerService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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

        GameState calcPhase = gamePhaseService.phaseOfInitialSession(player, updatedState);

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
            .isGuest(player.isGuest())
            .totalInitialCards(INITIAL_DECK_LENGTH)
            .build());
    }

    @PostMapping("/check_session")
    public ResponseEntity<?> checkSession(@RequestBody Map<String, String> body, Principal principal) {
        UUID clientId = null;

        try {
            clientId = controllerHelper.validateAndGetClientId(body, principal);
        } catch (Exception e) {
            // Ha nincs clientId vagy lejárt/hibás a token, azt itt elkapjuk,
            // és nem hagyjuk, hogy 400-as hibával elszálljon a szerver!
        }

        if (clientId == null) {
            return ResponseEntity.ok(Map.of(
                "status", "not_logged_in",
                "gameState", Map.of("targetPhase", "LOADING")
            ));
        }

        Player player = playerRepository.findByClientId(clientId).orElse(null);

        if (player == null || player.getCurrentGameState() == null) {
            return ResponseEntity.ok(Map.of(
                "status", "not_logged_in",
                "gameState", Map.of("targetPhase", "LOADING")
            ));
        }

        GameState currentState = player.getCurrentGameState();

        GameState phaseResult = gamePhaseService.phaseOfCheckSession(player, currentState);

        player.setCurrentGameState(phaseResult);
        playerRepository.save(player);

        Object responseDto = null;

        if (phaseResult.getTargetPhase() == PhaseState.BETTING) {
            responseDto = gameStateManager.buildGameStateBet(phaseResult)
                .toBuilder()
                .build();
        }

        if (phaseResult.getTargetPhase() != null && phaseResult.getTargetPhase().name().startsWith("MAIN")) {
            responseDto = gameStateManager.buildGameStateCheckBase(phaseResult)
                .toBuilder()
                .build();
        }

        if (phaseResult.getTargetPhase() != null && phaseResult.getTargetPhase().name().startsWith("SPLIT")) {
            responseDto = gameStateManager.buildGameStateSplitHand(phaseResult)
                .toBuilder()
                .build();
        }

        return ResponseEntity.ok(ApiResponse.builder()
            .status("success")
            .currentTokens(player.getTokens())
            .isGuest(player.isGuest())
            .username(player.getUsername())
            .gameState(responseDto)
            .gameStateHint("SESSION_LOADED")
            .build());
    }

    @PostMapping("/handle_auth")
    public ResponseEntity<?> handleAuth(@RequestBody Map<String, String> body, Principal principal, HttpSession session) {
        String email = body.get("email");
        String username = body.get("username");
        String password = body.get("password");

        Object isLoginVal = body.get("is_login");
        boolean isLogin = isLoginVal != null && Boolean.parseBoolean(String.valueOf(isLoginVal));

        UUID idempotencyKey = UUID.fromString(body.get("idempotencyKey"));

        if (playerService.hasEmptyFields(email, username, password)) {
            return ResponseEntity.ok(Map.of(
                "status", "IC"
            ));
        }

        // Kliens ID lekérése
        UUID currClientId = controllerHelper.validateAndGetClientId(body, principal);
        Player guestUser = (currClientId != null) ? playerRepository.findByClientId(currClientId).orElse(null) : null;

        Object result = playerService.handleUserAuth(email, username, password, isLogin, currClientId, idempotencyKey);

        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> errorMap = (Map<String, Object>) result;
            String status = (String) errorMap.get("status");

            if ("CONFLICT".equals(status)) {
                Player targetUser = playerRepository.findByEmailOrUsername(email, username).orElse(null);

                int targetTokens = (targetUser != null) ? targetUser.getTokens() : 0;
                int guestTokens = (guestUser != null) ? guestUser.getTokens() : 0;

                UUID targetuserId = null;
                if (targetUser != null) {
                    targetuserId = targetUser.getClientId();
                }

                if (guestUser != null && guestUser.getCurrentGameState() != null) {
                    playerRepository.save(guestUser);
                }

                Map<String, Object> gameStateMap = Map.of(
                    "target_phase", "CONFLICT"
                );

                ConflictResponse responseDto = new ConflictResponse(
                    status,
                    gameStateMap,
                    new ConflictResponse.ConflictData(
                        new ConflictResponse.Balance(targetTokens),
                        new ConflictResponse.Balance(guestTokens),
                        targetuserId
                    )
                );

                return ResponseEntity.ok(responseDto);
            }
            // Egyéb státuszok visszaküldése (pl. "IC", "IU", "UAE")
            return ResponseEntity.ok(errorMap);
        } else {
            Player user = (Player) result;
            session.setAttribute("user_id", user.getId());

            GameState game = user.getCurrentGameState();
            if (game == null) {
                game = new GameState(); // Biztonsági fallback
                user.setCurrentGameState(game);
            }

            user.setCurrentGameState(game);
            playerRepository.save(user);

            GameStateInit gameStateData = GameStateInit.builder()
                .deckLen(game.getDeckLen())
                .targetPhase(PhaseState.BETTING)
                .build();

            return ResponseEntity.ok(ApiResponse.<GameStateInit>builder()
                .status("success")
                .currentTokens(user.getTokens())
                .gameState(gameStateData)
                .username(user.getUsername())
                .isGuest(user.isGuest())
                .build());
        }
    }

    @PostMapping("/handle_conflict")
    public ResponseEntity<?> handleConflict(@RequestBody Map<String, String> body, Principal principal, HttpSession session) {
        boolean versionNew = Boolean.parseBoolean(String.valueOf(body.get("version_new")));
        UUID currClientId = controllerHelper.validateAndGetClientId(body, principal);
        Object result = controllerHelper.validateTargetUserIdSafely(body.get("target_user_id"));

        if (result instanceof Map<?, ?> errorMap) {
            return ResponseEntity.ok(Map.of("status", errorMap.get("status")));
        }

        Player targetUser = (Player) result;
        UUID targetUserId = targetUser.getClientId();

        Object res = playerService.handleConflict(currClientId, targetUserId, versionNew);
        if (res instanceof Map<?, ?> errorMap) {
            return ResponseEntity.ok(Map.of("status", errorMap.get("status")));
        }

        Player user = (Player) res;
        session.setAttribute("user_id", user.getClientId());

        GameState game = user.getCurrentGameState();

        if (game == null) {
            game = GameState.builder()
                .targetPhase(PhaseState.BETTING)
                .build();
            user.setCurrentGameState(game);
        } else {
            GamePhaseService.DeckPhaseResult deckAndPhase = gamePhaseService.phaseOfBetAndRetakeBet(game);
            game = game.toBuilder()
                .targetPhase(deckAndPhase.targetPhase())
                .prePhase(deckAndPhase.prePhase())
                .build();
            user.setCurrentGameState(game);
        }
        playerRepository.save(user);

        GameStateBet responseDto = gameStateManager.buildGameStateBet(game)
            .toBuilder()
            .build();

        String freshJwtToken = jwtService.generateToken(user.getClientId().toString());

        return ResponseEntity.ok(ApiResponse.<GameStateBet>builder()
            .status("success")
            .currentTokens(user.getTokens())
            .gameState(responseDto)
            .username(user.getUsername())
            .isGuest(user.isGuest())
            .clientId(user.getClientId())
            .token(freshJwtToken)
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
            .isGuest(player.isGuest())
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
            .isGuest(player.isGuest())
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
            .isGuest(player.isGuest())
            .gameState(responseDto)
            .gameStateHint("FORCE_RESTART_SUCCESSFUL")
            .build());
    }
}
