package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.ApiResponse;
import com.blackjack.blackjack.dto.SessionContext;
import com.blackjack.blackjack.dto.bet.BetRequest;
import com.blackjack.blackjack.dto.bet.GameStateBet;
import com.blackjack.blackjack.exception.GameRuleException;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.service.AuthenticationService;
import com.blackjack.blackjack.service.GameService;
import com.blackjack.blackjack.service.GameStateService;
import com.blackjack.blackjack.service.PlayerService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static com.blackjack.blackjack.common.GameConstants.INITIAL_DECK_LENGTH;
import static com.blackjack.blackjack.common.GameConstants.MINIMUM_BET;

@RestController
@RequestMapping("/api")
public class BetController {
    private static final Logger logger = LoggerFactory.getLogger(BetController.class);
    private final GameStateService gameStateService;
    private final PlayerService playerService;
    private final GameService gameService;
    private final AuthenticationService authenticationService;

    @Autowired
    public BetController(
        GameStateService gameStateService,
        PlayerService playerService,
        GameService gameService, AuthenticationService authenticationService) {
        this.gameStateService = gameStateService;
        this.playerService = playerService;
        this.gameService = gameService;
        this.authenticationService = authenticationService;
    }

    @PostMapping("/bet")
    public ResponseEntity<?> placeBet(
        @RequestBody BetRequest betRequest,
        HttpSession session) {

        SessionContext context = authenticationService.validateAndGetContext(session);

        Player player = context.player();
        Optional<GameState> gameStateOptional = context.gameStateOptional();

        int betAmount = betRequest.bet();
        if (betAmount < MINIMUM_BET) {
            throw new GameRuleException("BET_BELOW_MINIMUM");
        }
        if (player.getTokens() < betAmount) {
            throw new GameRuleException("NOT_ENOUGH_TOKENS_FOR_BET");
        }

        Player updatedPlayer;
        try {
            updatedPlayer = playerService.deductBet(player, betAmount);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "DEDUCTION_FAILED");
        }

        int updatedTokens = updatedPlayer.getTokens();

        GameState updatedGameState = gameService.placeBet(gameStateOptional, betAmount);

        int deckLength;
        if (updatedGameState.getDeck() == null) {
            deckLength = INITIAL_DECK_LENGTH;
        } else {
            deckLength = updatedGameState.getDeck().getDeckLength();
        }

        GameStateBet gameStateData = GameStateBet.builder()
            .bet(updatedGameState.getBet())
            .betList(updatedGameState.getBetList())
            .deckLen(deckLength)
            .build();

        return ResponseEntity.ok(ApiResponse.<GameStateBet>builder()
            .status("success")
            .gameState(gameStateData)
            .gameStateHint("BET_SUCCESSFULLY_PLACED")
            .currentTokens(updatedTokens)
            .build());
    }

    @PostMapping("/retake_bet")
    public ResponseEntity<?> retakeBet(
        HttpSession session) {

        SessionContext context = authenticationService.validateAndGetContext(session);

        UUID currentClientId = context.clientId();
        Player player = context.player();
        Optional<GameState> gameStateOptional = context.gameStateOptional();

        int amountToReturn = gameService.retakeBet(gameStateOptional);

        Player updatedPlayer;
        try {
            updatedPlayer = playerService.deductBet(player, amountToReturn);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "RETURN_FAILED");
        }

        int updatedTokens = updatedPlayer.getTokens();

        GameState updatedGameState = gameStateService.getGameState(currentClientId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, "ERROR"));

        int deckLength;
        if (updatedGameState.getDeck() == null) {
            deckLength = INITIAL_DECK_LENGTH;
        } else {
            deckLength = updatedGameState.getDeck().getDeckLength();
        }

        GameStateBet gameStateData = GameStateBet.builder()
            .bet(updatedGameState.getBet())
            .betList(updatedGameState.getBetList())
            .deckLen(deckLength)
            .build();

        return ResponseEntity.ok(ApiResponse.<GameStateBet>builder()
            .status("success")
            .gameState(gameStateData)
            .gameStateHint("BET_SUCCESSFULLY_RETRAKEN")
            .currentTokens(updatedTokens)
            .build());
    }
}
