package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.ActionRequest;
import com.blackjack.blackjack.dto.ApiResponse;
import com.blackjack.blackjack.dto.bet.GameStateBet;
import com.blackjack.blackjack.dto.others.*;
import com.blackjack.blackjack.exception.GameRuleException;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.service.GamePhaseService;
import com.blackjack.blackjack.service.GameService;
import com.blackjack.blackjack.service.GameStateManager;
import com.blackjack.blackjack.service.PlayerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

import static com.blackjack.blackjack.common.GameConstants.INITIAL_TOKENS;

@RestController
@RequestMapping("/api")
public class GameController {

    private final GameService gameService;
    private final PlayerService playerService;
    private final GameStateManager gameStateManager;
    private final GamePhaseService gamePhaseService;
    private final ControllerHelper controllerHelper;

    public GameController(GameService gameService, PlayerService playerService, GameStateManager gameStateManager, GamePhaseService gamePhaseService, ControllerHelper controllerHelper) {
        this.gameService = gameService;
        this.playerService = playerService;
        this.gameStateManager = gameStateManager;
        this.gamePhaseService = gamePhaseService;
        this.controllerHelper = controllerHelper;
    }

    // --- STANDARD JÁTÉK VÉGPONTOK ---
    @PostMapping("/create_deck")
    public ResponseEntity<?> createDeck(@RequestBody ActionRequest req, Principal principal) {
        return controllerHelper.handleAction(req, principal, "DECK_CREATED", gameStateManager::buildGameStateBet,
            state -> {
                GameState updated = gameStateManager.createNewDeck(state);
                return gamePhaseService.phaseOfCreateDeck(updated);
            },
            null);
    }

    @PostMapping("/start_game")
    public ResponseEntity<?> initializeNewRound(@RequestBody ActionRequest req, Principal principal) {
        return controllerHelper.handleAction(req, principal, "NEW_ROUND_INITIALIZED", gameStateManager::buildGameStateStart,
            state -> {
                GameState updated = gameService.initializeNewRound(state);
                return gamePhaseService.phaseOfStartGame(updated);
            },
            null);
    }

    @PostMapping("/hit")
    public ResponseEntity<?> playerHit(@RequestBody ActionRequest req, Principal principal) {
        return controllerHelper.handleAction(req, principal, "HIT_RECEIVED", gameStateManager::buildGameStateStart,
            state -> {
                GameState updated = gameService.playerHit(state);
                return gamePhaseService.phaseOfHit(updated, false);
            },
            null);
    }

    @PostMapping("/stand_and_rewards")
    public ResponseEntity<?> standAndRewards(@RequestBody ActionRequest req, Principal principal) {
        Player player = playerService.getAndValidatePlayer(req.getClientId());
        GameState current = player.getCurrentGameState();

        if (current == null || !current.isRoundActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active round to stand");
        }
        GameState stoodState = gameService.playerStand(current);
        GameService.TransactionResult result = gameService.calculateRewards(stoodState);
        int finalTokens = player.getTokens() + result.transactionResult();

        return controllerHelper.handleAction(
            req, principal,
            "REWARDS_PROCESSED",
            gameStateManager::buildGameStateRewards,
            state -> {
                GameState rewardState = result.gameState();
                return gamePhaseService.phaseOfStand(rewardState, finalTokens);
            },
            result.transactionResult()
        );
    }

    @PostMapping("/double_request")
    public ResponseEntity<?> doubleRequest(@RequestBody ActionRequest req, Principal principal) {
        Player p = playerService.getAndValidatePlayer(req.getClientId());
        GameState current = p.getCurrentGameState();

        if (current == null || !current.isRoundActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active round");
        }

        if (current.getPlayer() == null) {
            throw new IllegalStateException("Critical error: Player object is missing from GameState during Double request");
        }

        if (current.getPlayer().hand().size() != 2) {
            throw new GameRuleException("DOUBLE NOT ALLOWED");
        }

        int betToDeduct = current.getBet();
        if (p.getTokens() < betToDeduct) {
            throw new GameRuleException("NOT_ENOUGH_TOKENS");
        }

        return controllerHelper.handleAction(req, principal, "DOUBLE_PROCESSED", gameStateManager::buildGameStateDouble, state -> {
            GameState afterHit = gameService.playerHit(state);
            GameState afterBet = gameService.applyDoubleBet(afterHit, betToDeduct);
            return gamePhaseService.phaseOfHit(afterBet, true);
        }, -betToDeduct);
    }

    @PostMapping("/ins_request")
    public ResponseEntity<?> insuranceRequest(@RequestBody ActionRequest req, Principal principal) {
        Player player = playerService.getAndValidatePlayer(req.getClientId());
        GameService.TransactionResult result = gameService.insuranceRequest(player.getCurrentGameState());

        return controllerHelper.handleAction(req, principal, "INS_PROCESSED",
            gameStateManager::buildGameStateInsurance,
            state -> {
                GameState rewardState = result.gameState();
                return gamePhaseService.phaseOfInsRequest(rewardState);
            },
            result.transactionResult()
        );
    }

    // --- SPLIT VÉGPONTOK ---
    @PostMapping("/split_request")
    public ResponseEntity<?> splitRequest(@RequestBody ActionRequest req, Principal principal) {
        Player p = playerService.getAndValidatePlayer(req.getClientId());
        GameState current = p.getCurrentGameState();

        if (current == null || !current.isRoundActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active round");
        }

        if (current.getPlayer() == null) {
            throw new IllegalStateException("Critical error: Player object is missing during Split request");
        }

        if (current.getPlayer().hand().size() != 2) {
            throw new GameRuleException("SPLIT_NOT_ALLOWED_MORE_THAN_TWO_CARDS");
        }

        int betToDeduct = current.getBet();
        if (p.getTokens() < betToDeduct) {
            throw new GameRuleException("NOT_ENOUGH_TOKENS");
        }

        return controllerHelper.handleAction(req, principal, "SPLIT_PROCESSED", gameStateManager::buildGameStateSplitHand, state -> {
            GameState splitted = gameService.splitHand(state);
            return gamePhaseService.phaseOfSplitRequest(splitted);
        }, -betToDeduct);
    }

    @PostMapping("/add_to_players_list_by_stand")
    public ResponseEntity<?> addToPlayersByStand(@RequestBody ActionRequest req, Principal principal) {
        return controllerHelper.handleAction(req, principal, "HAND_SAVED", gameStateManager::buildGameStateAddToPlayers, state -> {
            GameState added = gameService.addToPlayersListByStand(state);
            return gamePhaseService.phaseOfAddToPlayersListByStand(added);
        }, null);
    }

    @PostMapping("/add_split_player_to_game")
    public ResponseEntity<?> addSplitPlayerToGame(@RequestBody ActionRequest req, Principal principal) {
        return controllerHelper.handleAction(req, principal, "NEXT_SPLIT_HAND_ACTIVE", gameStateManager::buildGameStateSplitHand, state -> {
            GameState added = gameService.addSplitPlayerToGame(state);
            return gamePhaseService.phaseOfAddSplitPlayerToGame(added);
        }, null);
    }

    @PostMapping("/add_player_from_players")
    public ResponseEntity<?> addPlayerFromPlayers(@RequestBody ActionRequest req, Principal principal) {
        return controllerHelper.handleAction(req, principal, "ALL_HANDS_COLLECTED", gameStateManager::buildGameStateAddPlayerFromPlayers, state -> {
            GameState added = gameService.addPlayerFromPlayers(state);
            return gamePhaseService.phaseOfAddPlayerFromPlayers(added);
        }, null);
    }

    @PostMapping("/split_hit")
    public ResponseEntity<?> splitHit(@RequestBody ActionRequest req, Principal principal) {
        return controllerHelper.handleAction(req, principal, "SPLIT_HIT_RECEIVED", gameStateManager::buildGameStateSplitHand, (state) -> {
                GameState updated = gameService.playerHit(state);
                return gamePhaseService.phaseOfHit(updated, false);
            },
            null);
    }

    @PostMapping("/split_stand_and_rewards")
    public ResponseEntity<?> splitStandAndRewards(@RequestBody ActionRequest req, Principal principal) {
        Player player = playerService.getAndValidatePlayer(req.getClientId());
        GameState current = player.getCurrentGameState();

        if (current == null || !current.isRoundActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active round to stand");
        }

        GameState stoodState = gameService.playerStand(current);
        GameService.TransactionResult result = gameService.calculateRewards(stoodState);
        int finalTokens = player.getTokens() + result.transactionResult();

        return controllerHelper.handleAction(
            req, principal,
            "REWARDS_PROCESSED",
            gameStateManager::buildGameStateSplitRewards,
            state -> {
                GameState rewardState = result.gameState();
                return gamePhaseService.phaseOfStand(rewardState, finalTokens);
            },
            result.transactionResult()
        );
    }

    @PostMapping("/split_double_request")
    public ResponseEntity<?> splitDoubleRequest(@RequestBody ActionRequest req, Principal principal) {
        Player p = playerService.getAndValidatePlayer(req.getClientId());

        GameState current = p.getCurrentGameState();

        if (current == null || !current.isRoundActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active round for split double");
        }

        if (current.getPlayer() == null) {
            throw new IllegalStateException("Critical error: Player object is missing from GameState during Double request");
        }

        if (current.getPlayer().hand().size() != 2) {
            throw new GameRuleException("DOUBLE_ONLY_ALLOWED_ON_STARTING_HAND");
        }

        int betToDeduct = current.getPlayer().bet();
        if (p.getTokens() < betToDeduct) {
            throw new GameRuleException("NOT_ENOUGH_TOKENS_FOR_DOUBLE");
        }

        return controllerHelper.handleAction(
            req, principal,
            "SPLT_REQUEST_PROCESSED",
            gameStateManager::buildGameStateSplitHand,
            state -> {
                GameState afterHit = gameService.playerHit(state);
                GameState afterBet = gameService.applyDoubleBet(afterHit, betToDeduct);
                return gamePhaseService.phaseOfHit(afterBet, true);
            },
            -betToDeduct
        );
    }

    @PostMapping("/set_restart")
    public ResponseEntity<?> setRestart(@RequestBody ActionRequest actionRequest, Principal principal) {
        controllerHelper.validateClientAuthorization(principal, String.valueOf(actionRequest.getClientId()));
        Player player = playerService.getAndValidatePlayer(actionRequest.getClientId());

        Player playerToUpdate = player.toBuilder()
            .tokens(INITIAL_TOKENS)
            .currentGameState(null)
            .idempotencyKey(actionRequest.getIdempotencyKey())
            .build();

        Player savedPlayer = playerService.savePlayer(playerToUpdate);
        GameStateBet emptyGameState = gamePhaseService.phaseOfSetRestart(null);

        return ResponseEntity.ok(ApiResponse.builder()
            .status("success")
            .currentTokens(savedPlayer.getTokens())
            .isGuest(savedPlayer.isGuest())
            .gameState(emptyGameState)
            .gameStateHint("GAME_RESET_TO_NULL")
            .build());
    }
}
