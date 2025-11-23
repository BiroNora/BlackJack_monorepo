package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.ApiResponse;
import com.blackjack.blackjack.dto.SessionContext;
import com.blackjack.blackjack.dto.bet.GameStateBet;
import com.blackjack.blackjack.dto.others.GameStateDouble;
import com.blackjack.blackjack.dto.others.GameStateRewards;
import com.blackjack.blackjack.dto.others.GameStateStart;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.service.AuthenticationService;
import com.blackjack.blackjack.service.GameService;
import com.blackjack.blackjack.service.GameStateService;
import com.blackjack.blackjack.service.PlayerService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@RestController
@RequestMapping("/api")
public class GameController {
    private static final Logger logger = LoggerFactory.getLogger(GameController.class);
    private final GameService gameService;
    private final AuthenticationService authenticationService;
    private final PlayerService playerService;
    private final GameStateService gameStateService;

    public GameController(GameService gameService, AuthenticationService authenticationService, PlayerService playerService, GameStateService gameStateService
    ) {
        this.gameService = gameService;
        this.authenticationService = authenticationService;
        this.playerService = playerService;
        this.gameStateService = gameStateService;
    }

    @PostMapping("/create_deck")
    public ResponseEntity<?> createDeck(
        HttpSession session) {
        SessionContext context = authenticationService.validateAndGetContext(session);

        Player player = context.player();
        int tokens = player.getTokens();

        Optional<GameState> gameStateOptional = context.gameStateOptional();
        GameState updatedGameState = gameService.createNewDeck(gameStateOptional);

        session.setAttribute("gameState", updatedGameState);

        GameStateBet gameStateData = GameStateBet.builder()
            .bet(updatedGameState.getBet())
            .betList(updatedGameState.getBetList())
            .deckLen(updatedGameState.getDeckLen())
            .build();

        return ResponseEntity.ok(ApiResponse.<GameStateBet>builder()
            .status("success")
            .gameState(gameStateData)
            .gameStateHint("DECK_CREATED")
            .currentTokens(tokens)
            .build());
    }

    @PostMapping("/start_game")
    public ResponseEntity<?> initializeNewRound(
        HttpSession session) {
        SessionContext context = authenticationService.validateAndGetContext(session);

        Player player = context.player();
        int tokens = player.getTokens();

        Optional<GameState> gameStateOptional = context.gameStateOptional();
        GameState updatedGameState = gameService.initializeNewRound(gameStateOptional);

        session.setAttribute("gameState", updatedGameState);

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
            .currentTokens(tokens)
            .build());
    }

    @PostMapping("/hit")
    public ResponseEntity<?> playerHit(
        HttpSession session) {
        SessionContext context = authenticationService.validateAndGetContext(session);

        Player player = context.player();
        int tokens = player.getTokens();

        Optional<GameState> gameStateOptional = context.gameStateOptional();
        GameState updatedGameState = gameService.playerHit(gameStateOptional);

        session.setAttribute("gameState", updatedGameState);

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
            .gameStateHint("HIT_RECIEVED")
            .currentTokens(tokens)
            .build());
    }

    @PostMapping("/stand_and_rewards")
    public ResponseEntity<?> standAndRewards(
        HttpSession session) {
        SessionContext context = authenticationService.validateAndGetContext(session);
        Player player = context.player();

        Optional<GameState> gameStateOptional = context.gameStateOptional();

        GameState stoodGameState = gameService.playerStand(gameStateOptional);
        GameService.TransactionResult rewardResult = gameService.calculateRewards(stoodGameState);

        GameState updatedGameState = rewardResult.gameState();
        int rewardAmount = rewardResult.transactionResult();

        try {
            playerService.updateTokens(player, rewardAmount);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Hiba történt a jutalom feldolgozása során.");
        }

        session.setAttribute("gameState", stoodGameState);

        GameStateRewards gameStateData = GameStateRewards.builder()
            .player(updatedGameState.getPlayer())
            .dealerUnmasked(updatedGameState.getDealerUnmasked())
            .deckLen(updatedGameState.getDeckLen())
            .bet(updatedGameState.getPlayer().getBet())
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

    @PostMapping("/double_request")
    public ResponseEntity<?> doubleRequest(
        HttpSession session) {
        SessionContext context = authenticationService.validateAndGetContext(session);
        Player player = context.player();
        Optional<GameState> gameStateOptional = context.gameStateOptional();

        GameState initialGameState = gameStateOptional.orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "CLIENT_ERROR_SPECIFIC: A Double Down kéréshez játékállapot szükséges."
        ));

        int betToDeduct = initialGameState.getPlayer().getBet();

        if (player.getTokens() < betToDeduct) {
            return ResponseEntity.badRequest().body("INSUFFICIENT_FUNDS_FOR_DOUBLE");
        }

        try {
            playerService.deductBet(player, betToDeduct);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("INTERNAL_UPDATE_FAILED");
        }

        GameState gameStateAfterHit = gameService.playerHit(gameStateOptional);
        GameState updatedGameState = gameService.doubleRequest(gameStateAfterHit, betToDeduct);
        gameStateService.saveGameState(updatedGameState);
        session.setAttribute("gameState", updatedGameState);

        GameStateDouble gameStateDouble = GameStateDouble.builder()
            .player(updatedGameState.getPlayer())
            .deckLen(updatedGameState.getDeckLen())
            .isRoundActive(updatedGameState.isRoundActive())
            .build();

        return ResponseEntity.ok(ApiResponse.builder()
            .status("success")
            .currentTokens(player.getTokens())
            .gameState(gameStateDouble)
            .gameStateHint("DOUBLE_DOWN_PROCESSED")
            .build());
    }
}
