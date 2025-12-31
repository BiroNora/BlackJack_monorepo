package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.ActionRequest;
import com.blackjack.blackjack.dto.ApiResponse;
import com.blackjack.blackjack.dto.GameContext;
import com.blackjack.blackjack.dto.bet.GameStateBet;
import com.blackjack.blackjack.dto.others.*;
import com.blackjack.blackjack.exception.GameRuleException;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.repository.PlayerRepository;
import com.blackjack.blackjack.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static com.blackjack.blackjack.common.GameConstants.INITIAL_TOKENS;

@RestController
@RequestMapping("/api")
public class GameController {
    private static final Logger logger = LoggerFactory.getLogger(GameController.class);
    private final GameService gameService;
    private final AuthenticationService authenticationService;
    private final PlayerService playerService;
    private final GameStateService gameStateService;
    private final GameStateManager gameStateManager;
    private final PlayerRepository playerRepository;

    public GameController(GameService gameService, AuthenticationService authenticationService, PlayerService playerService, GameStateService gameStateService, PlayerRepository playerRepository, GameStateManager gameStateManager, PlayerRepository playerRepository1
    ) {
        this.gameService = gameService;
        this.authenticationService = authenticationService;
        this.playerService = playerService;
        this.gameStateService = gameStateService;
        this.gameStateManager = gameStateManager;
        this.playerRepository = playerRepository1;
    }

    @PostMapping("/create_deck")
    public ResponseEntity<?> createDeck(
        @RequestBody ActionRequest actionRequest) {
        GameContext context = authenticationService.validateAndGetContext(actionRequest.getClientId());
        Player player = context.player();

        GameState currentState = context.gameStateOptional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No GameState found"));

        GameState updatedGameState = gameStateManager.createNewDeck(currentState);
        gameStateService.saveGameState(updatedGameState);

        GameStateBet gameStateData = GameStateBet.builder()
            .bet(updatedGameState.getBet())
            .betList(updatedGameState.getBetList())
            .deckLen(updatedGameState.getDeckLen())
            .build();

        return ResponseEntity.ok(ApiResponse.<GameStateBet>builder()
            .status("success")
            .gameState(gameStateData)
            .gameStateHint("DECK_CREATED")
            .currentTokens(player.getTokens())
            .build());
    }

    @PostMapping("/start_game")
    public ResponseEntity<?> initializeNewRound(@RequestBody ActionRequest actionRequest) {
        GameContext context = authenticationService.validateAndGetContext(actionRequest.getClientId());
        Player player = context.player();

        GameState currentState = context.gameStateOptional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No GameState found"));

        GameState updatedGameState = gameService.initializeNewRound(currentState);

        gameStateService.saveGameState(updatedGameState);

        GameStateStart gameStateData = GameStateStart.builder()
            .player(updatedGameState.getPlayer())
            .dealerMasked(updatedGameState.getDealerMasked())
            .bet(updatedGameState.getBet())
            .deckLen(updatedGameState.getDeckLen())
            .isRoundActive(updatedGameState.isRoundActive())
            .build();

        return ResponseEntity.ok(ApiResponse.<GameStateStart>builder()
            .status("success")
            .gameState(gameStateData)
            .gameStateHint("NEW_ROUND_INITIALIZED")
            .currentTokens(player.getTokens())
            .build());
    }

    @PostMapping("/hit")
    public ResponseEntity<?> playerHit(@RequestBody ActionRequest actionRequest) {
        GameContext context = authenticationService.validateAndGetContext(actionRequest.getClientId());
        Player player = context.player();

        GameState currentState = context.gameStateOptional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No GameState found"));

        GameState updatedGameState = gameService.playerHit(currentState);

        gameStateService.saveGameState(updatedGameState);

        GameStateStart gameStateData = GameStateStart.builder()
            .player(updatedGameState.getPlayer())
            .dealerMasked(updatedGameState.getDealerMasked())
            .bet(updatedGameState.getBet())
            .deckLen(updatedGameState.getDeckLen())
            .isRoundActive(updatedGameState.isRoundActive())
            .build();

        return ResponseEntity.ok(ApiResponse.<GameStateStart>builder()
            .status("success")
            .gameState(gameStateData)
            .gameStateHint("HIT_RECEIVED") // Kijavítottam a typo-t: RECIEVED -> RECEIVED
            .currentTokens(player.getTokens())
            .build());
    }

    @PostMapping("/stand_and_rewards")
    public ResponseEntity<?> standAndRewards(@RequestBody ActionRequest actionRequest) {
        GameContext context = authenticationService.validateAndGetContext(actionRequest.getClientId());
        Player player = context.player();
        GameState currentState = context.gameStateOptional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No GameState found"));

        GameState stoodGameState = gameService.playerStand(currentState);

        GameService.TransactionResult rewardResult = gameService.calculateRewards(stoodGameState);
        GameState finalGameState = rewardResult.gameState();
        int rewardAmount = rewardResult.transactionResult();

        try {
            playerService.updateTokens(player, rewardAmount);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "TOKEN_UPDATE_FAILED");
        }

        gameStateService.saveGameState(finalGameState);

        GameStateRewards gameStateData = GameStateRewards.builder()
            .player(finalGameState.getPlayer())
            .dealerUnmasked(finalGameState.getDealerUnmasked())
            .deckLen(finalGameState.getDeckLen())
            .bet(finalGameState.getBet())
            .winner(finalGameState.getWinner())
            .isRoundActive(finalGameState.isRoundActive())
            .build();

        return ResponseEntity.ok(ApiResponse.builder()
            .status("success")
            .currentTokens(player.getTokens())
            .gameState(gameStateData)
            .gameStateHint("REWARDS_PROCESSED")
            .build());
    }

    @PostMapping("/double_request")
    public ResponseEntity<?> doubleRequest(@RequestBody ActionRequest actionRequest) {
        GameContext context = authenticationService.validateAndGetContext(actionRequest.getClientId());
        Player player = context.player();

        GameState initialGameState = context.gameStateOptional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "GAME_NOT_FOUND"));

        assert initialGameState.getPlayer() != null;
        int betToDeduct = initialGameState.getPlayer().bet();

        if (player.getTokens() < betToDeduct) {
            throw new GameRuleException("NOT_ENOUGH_TOKENS_FOR_DOUBLE");
        }
        if (initialGameState.getPlayer().hand().size() != 2) {
            throw new GameRuleException("DOUBLE_ONLY_ALLOWED_ON_STARTING_HAND");
        }

        GameState stateAfterHit = gameService.playerHit(initialGameState);

        GameState finalState = gameService.applyDoubleBet(stateAfterHit, betToDeduct);

        playerService.deductBet(player, betToDeduct);
        gameStateService.saveGameState(finalState);

        GameStateDouble gameStateDouble = GameStateDouble.builder()
            .player(finalState.getPlayer())
            .deckLen(finalState.getDeckLen())
            .isRoundActive(finalState.isRoundActive())
            .build();

        return ResponseEntity.ok(ApiResponse.builder()
            .status("success")
            .currentTokens(player.getTokens())
            .gameState(gameStateDouble)
            .gameStateHint("DOUBLE_DOWN_PROCESSED")
            .build());
    }

    @PostMapping("/ins_request")
    public ResponseEntity<?> insuranceRequest(@RequestBody ActionRequest actionRequest) {
        GameContext context = authenticationService.validateAndGetContext(actionRequest.getClientId());
        Player player = context.player();

        GameState currentState = context.gameStateOptional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Game not found"));

        int insuranceCost = (int) Math.ceil(currentState.getBet() / 2.0);
        if (player.getTokens() < insuranceCost) {
            throw new GameRuleException("NOT_ENOUGH_TOKENS_FOR_INSURANCE");
        }

        GameService.TransactionResult insResult = gameService.insuranceRequest(currentState);
        GameState updatedGameState = insResult.gameState();
        int transactionAmount = insResult.transactionResult();

        try {
            playerService.updateTokens(player, transactionAmount);
            gameStateService.saveGameState(updatedGameState); // Most mentünk!
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "DATABASE_ERROR");
        }

        assert updatedGameState.getPlayer() != null;
        GameStateInsurance gameStateData = GameStateInsurance.builder()
            .player(updatedGameState.getPlayer())
            .natural21(updatedGameState.getNatural21())
            .deckLen(updatedGameState.getDeckLen())
            .bet(updatedGameState.getPlayer().bet())
            .isRoundActive(updatedGameState.isRoundActive())
            .dealerMasked(updatedGameState.getDealerMasked())
            .dealerUnmasked(updatedGameState.getDealerUnmasked())
            .build();

        return ResponseEntity.ok(ApiResponse.builder()
            .status("success")
            .currentTokens(player.getTokens())
            .gameState(gameStateData)
            .gameStateHint("INSURANCE_PROCESSED")
            .build());
    }

    @PostMapping("/rewards")
    public ResponseEntity<?> rewards(@RequestBody ActionRequest actionRequest) {
        GameContext context = authenticationService.validateAndGetContext(actionRequest.getClientId());
        Player player = context.player();
        Optional<GameState> gameStateOptional = context.gameStateOptional();

        GameState gameState = gameStateOptional.orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "CLIENT_ERROR_SPECIFIC"
        ));

        GameService.TransactionResult rewardResult = gameService.calculateRewards(gameState);
        GameState updatedGameState = rewardResult.gameState();
        int rewardAmount = rewardResult.transactionResult();

        try {
            playerService.updateTokens(player, rewardAmount);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR");
        }

        gameStateService.saveGameState(updatedGameState);

        assert updatedGameState.getPlayer() != null;
        GameStateRewards gameStateData = GameStateRewards.builder()
            .player(updatedGameState.getPlayer())
            .dealerUnmasked(updatedGameState.getDealerUnmasked())
            .deckLen(updatedGameState.getDeckLen())
            .bet(updatedGameState.getPlayer().bet())
            .winner(updatedGameState.getWinner())
            .isRoundActive(updatedGameState.isRoundActive())
            .build();

        return ResponseEntity.ok(ApiResponse.builder()
            .status("success")
            .currentTokens(player.getTokens())
            .gameState(gameStateData)
            .gameStateHint("REWARDS_PROCESSED")
            .build());
    }

    // SPLIT part
    @PostMapping("/split_request")
    public ResponseEntity<?> splitRequest(@RequestBody ActionRequest actionRequest) {
        GameContext context = authenticationService.validateAndGetContext(actionRequest.getClientId());
        Player player = context.player();
        GameState initialGameState = context.gameStateOptional().orElseThrow(() ->
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "NO_ACTIVE_GAME"));

        assert initialGameState.getPlayer() != null;
        int betToDeduct = initialGameState.getPlayer().bet();
        if (player.getTokens() < betToDeduct) {
            throw new GameRuleException("NOT_ENOUGH_TOKENS_FOR_SPLIT");
        }

        playerService.deductBet(player, betToDeduct);

        GameState updatedGameState = gameService.splitHand(initialGameState);
        gameStateService.saveGameState(updatedGameState);

        GameStateSplitHand responseState = GameStateSplitHand.builder()
            .player(updatedGameState.getPlayer())
            .dealerHandMasked(updatedGameState.getDealerMasked())
            .aces(updatedGameState.isAces())
            .players(updatedGameState.getPlayers())
            .splitReq(updatedGameState.getSplitReq())
            .deckLen(updatedGameState.getDeckLen())
            .bet(updatedGameState.getBet())
            .isRoundActive(updatedGameState.isRoundActive())
            .build();

        return ResponseEntity.ok(ApiResponse.builder()
            .status("success")
            .currentTokens(player.getTokens()) // A deductBet után frissült player objektum
            .gameState(responseState)
            .gameStateHint("SPLT_REQUEST_PROCESSED")
            .build());
    }

    @PostMapping("/add_to_players_list_by_stand")
    public ResponseEntity<?> addToPlayersByStand(@RequestBody ActionRequest actionRequest) {
        GameContext context = authenticationService.validateAndGetContext(actionRequest.getClientId());
        Player player = context.player();
        GameState initialGameState = context.gameStateOptional().orElseThrow(() ->
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "NO_ACTIVE_GAME"));

        GameState updatedGameState = gameService.addToPlayersListByStand(initialGameState);
        gameStateService.saveGameState(updatedGameState);

        GameStateAddToPlayersByStand gameStateSplitHand = GameStateAddToPlayersByStand.builder()
            .player(updatedGameState.getPlayer())
            .aces(updatedGameState.isAces())
            .players(updatedGameState.getPlayers())
            .splitReq(updatedGameState.getSplitReq())
            .deckLen(updatedGameState.getDeckLen())
            .bet(updatedGameState.getBet())
            .isRoundActive(updatedGameState.isRoundActive())
            .dealerMasked(updatedGameState.getDealerMasked())
            .dealerUnmasked(updatedGameState.getDealerUnmasked())
            .build();

        return ResponseEntity.ok(ApiResponse.builder()
            .status("success")
            .currentTokens(player.getTokens())
            .gameState(gameStateSplitHand)
            .gameStateHint("SPLT_REQUEST_PROCESSED")
            .build());
    }

    @PostMapping("/add_split_player_to_game")
    public ResponseEntity<?> addSplitPlayerToGame(@RequestBody ActionRequest actionRequest) {
        GameContext context = authenticationService.validateAndGetContext(actionRequest.getClientId());
        Player player = context.player();
        GameState initialGameState = context.gameStateOptional().orElseThrow(() ->
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "NO_ACTIVE_GAME"));

        GameState updatedGameState = gameService.addSplitPlayerToGame(initialGameState);
        gameStateService.saveGameState(updatedGameState);

        GameStateSplitHand gameStateSplitHand = GameStateSplitHand.builder()
            .player(updatedGameState.getPlayer())
            .dealerHandMasked(updatedGameState.getDealerMasked())
            .aces(updatedGameState.isAces())
            .players(updatedGameState.getPlayers())
            .splitReq(updatedGameState.getSplitReq())
            .deckLen(updatedGameState.getDeckLen())
            .bet(updatedGameState.getBet())
            .isRoundActive(updatedGameState.isRoundActive())
            .build();

        return ResponseEntity.ok(ApiResponse.builder()
            .status("success")
            .currentTokens(player.getTokens())
            .gameState(gameStateSplitHand)
            .gameStateHint("SPLT_REQUEST_PROCESSED")
            .build());
    }

    @PostMapping("/add_player_from_players")
    public ResponseEntity<?> addPlayerFromPlayers(@RequestBody ActionRequest actionRequest) {
        GameContext context = authenticationService.validateAndGetContext(actionRequest.getClientId());
        Player player = context.player();
        GameState initialGameState = context.gameStateOptional().orElseThrow(() ->
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "NO_ACTIVE_GAME"));

        GameState updatedGameState = gameService.addPlayerFromPlayers(initialGameState);
        gameStateService.saveGameState(updatedGameState);

        GameStateAddPlayerFromPlayers gameStateAddPlayerFromPlayers = GameStateAddPlayerFromPlayers.builder()
            .player(updatedGameState.getPlayer())
            .dealerUnmasked(updatedGameState.getDealerUnmasked())
            .aces(updatedGameState.isAces())
            .players(updatedGameState.getPlayers())
            .splitReq(updatedGameState.getSplitReq())
            .deckLen(updatedGameState.getDeckLen())
            .bet(updatedGameState.getBet())
            .isRoundActive(updatedGameState.isRoundActive())
            .build();

        return ResponseEntity.ok(ApiResponse.builder()
            .status("success")
            .currentTokens(player.getTokens())
            .gameState(gameStateAddPlayerFromPlayers)
            .gameStateHint("SPLT_REQUEST_PROCESSED")
            .build());
    }

    @PostMapping("/split_hit")
    public ResponseEntity<?> splitHit(@RequestBody ActionRequest actionRequest) {
        GameContext context = authenticationService.validateAndGetContext(actionRequest.getClientId());
        Player player = context.player();

        GameState gameState = context.gameStateOptional().orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "CLIENT_ERROR_SPECIFIC"
        ));

        GameState updatedGameState = gameService.playerHit(gameState);
        gameStateService.saveGameState(updatedGameState);

        GameStateSplitHand gameStateSplitHand = GameStateSplitHand.builder()
            .player(updatedGameState.getPlayer())
            .dealerHandMasked(updatedGameState.getDealerMasked())
            .aces(updatedGameState.isAces())
            .players(updatedGameState.getPlayers())
            .splitReq(updatedGameState.getSplitReq())
            .deckLen(updatedGameState.getDeckLen())
            .bet(updatedGameState.getBet())
            .isRoundActive(updatedGameState.isRoundActive())
            .build();

        return ResponseEntity.ok(ApiResponse.builder()
            .status("success")
            .currentTokens(player.getTokens())
            .gameState(gameStateSplitHand)
            .gameStateHint("SPLT_HIT_REQUEST_PROCESSED")
            .build());
    }

    @PostMapping("/split_stand_and_rewards")
    public ResponseEntity<?> splitStandAndRewards(@RequestBody ActionRequest actionRequest) {
        GameContext context = authenticationService.validateAndGetContext(actionRequest.getClientId());
        Player player = context.player();
        GameState gameState = context.gameStateOptional().orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "CLIENT_ERROR_SPECIFIC"
        ));

        GameState stoodGameState = gameService.playerStand(gameState);

        GameService.TransactionResult rewardResult = gameService.calculateRewards(stoodGameState);
        GameState updatedGameState = rewardResult.gameState();
        int rewardAmount = rewardResult.transactionResult();

        try {
            playerService.updateTokens(player, rewardAmount);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR");
        }

        gameStateService.saveGameState(updatedGameState);

        GameStateSplitStandAndRewards gameStateData = GameStateSplitStandAndRewards.builder()
            .player(updatedGameState.getPlayer())
            .dealerUnmasked(updatedGameState.getDealerUnmasked())
            .players(updatedGameState.getPlayers())
            .winner(updatedGameState.getWinner())
            .splitReq(updatedGameState.getSplitReq())
            .deckLen(updatedGameState.getDeckLen())
            .bet(updatedGameState.getBet())
            .isRoundActive(updatedGameState.isRoundActive())
            .build();

        return ResponseEntity.ok(ApiResponse.builder()
            .status("success")
            .currentTokens(player.getTokens())
            .gameState(gameStateData)
            .gameStateHint("REWARDS_PROCESSED")
            .build());
    }

    @PostMapping("/split_double_request")
    public ResponseEntity<?> splitDoubleRequest(@RequestBody ActionRequest actionRequest) {
        GameContext context = authenticationService.validateAndGetContext(actionRequest.getClientId());
        Player player = context.player();
        GameState initialGameState = context.gameStateOptional().orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "CLIENT_ERROR_SPECIFIC"
        ));

        assert initialGameState.getPlayer() != null;
        int betToDeduct = initialGameState.getPlayer().bet();

        if (player.getTokens() < betToDeduct) {
            throw new GameRuleException("NOT_ENOUGH_TOKENS_FOR_DOUBLE");
        }

        try {
            playerService.deductBet(player, betToDeduct);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR");
        }

        GameState gameStateAfterHit = gameService.playerHit(initialGameState);
        GameState updatedGameState = gameService.applyDoubleBet(gameStateAfterHit, betToDeduct);
        gameStateService.saveGameState(updatedGameState);

        GameStateSplitHand gameStateSplitHand = GameStateSplitHand.builder()
            .player(updatedGameState.getPlayer())
            .dealerHandMasked(updatedGameState.getDealerMasked())
            .aces(updatedGameState.isAces())
            .players(updatedGameState.getPlayers())
            .splitReq(updatedGameState.getSplitReq())
            .deckLen(updatedGameState.getDeckLen())
            .bet(updatedGameState.getBet())
            .isRoundActive(updatedGameState.isRoundActive())
            .build();

        return ResponseEntity.ok(ApiResponse.builder()
            .status("success")
            .currentTokens(player.getTokens())
            .gameState(gameStateSplitHand)
            .gameStateHint("SPLT_REQUEST_PROCESSED")
            .build());
    }


    @PostMapping("/set_restart")
    public ResponseEntity<?> setRestart(@RequestBody ActionRequest actionRequest) {
        GameContext context = authenticationService.validateAndGetContext(actionRequest.getClientId());
        Player player = context.player();

        // 1. Kontextus kicsomagolása (Ha nincs állapota, dobunk egy hibát)
        GameState currentState = context.gameStateOptional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No game state to restart"));

        // 2. TOKENEK RESETELÉSE
        // Ezt érdemes egyben kezelni: ha a token reset sikerül, csak akkor megyünk tovább
        Player updatedPlayer;
        try {
            updatedPlayer = playerService.resetTokens(player, INITIAL_TOKENS);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "TOKEN_RESET_FAILED");
        }

        // 3. JÁTÉKÁLLAPOT RESETELÉSE (Láncolt logika, nincs mentés a szervizben)
        GameState updatedGameState = gameService.restartGame(currentState);

        // 4. MENTÉS (Itt szinkronizáljuk a DB-t és a session-t)
        gameStateService.saveGameState(updatedGameState);

        // 5. DTO Összeállítása
        GameStateBet gameStateData = GameStateBet.builder()
            .bet(updatedGameState.getBet())
            .betList(updatedGameState.getBetList())
            .deckLen(updatedGameState.getDeckLen()) // Használd a friss állapot hosszát
            .build();

        return ResponseEntity.ok(ApiResponse.<GameStateBet>builder()
            .status("success")
            .gameState(gameStateData)
            .gameStateHint("GAME_AND_TOKENS_RESET_SUCCESS")
            .currentTokens(updatedPlayer.getTokens())
            .build());
    }
}
