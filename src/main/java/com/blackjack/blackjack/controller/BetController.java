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
import com.blackjack.blackjack.service.GameService.RetakeResult;
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

import static com.blackjack.blackjack.common.GameConstants.MINIMUM_BET;

@RestController
@RequestMapping("/api")
public class BetController {
    private static final Logger logger = LoggerFactory.getLogger(GameService.class);
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
    public ResponseEntity<?> placeBet(@RequestBody BetRequest betRequest, HttpSession session) {
        SessionContext context = authenticationService.validateAndGetContext(session);
        Player player = context.player();

        GameState currentState = context.gameStateOptional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "GAME_NOT_FOUND"));

        int betAmount = betRequest.bet();

        if (betAmount < MINIMUM_BET) {
            throw new GameRuleException("BET_BELOW_MINIMUM");
        }
        if (player.getTokens() < betAmount) {
            throw new GameRuleException("NOT_ENOUGH_TOKENS_FOR_BET");
        }

        Player updatedPlayer = playerService.deductBet(player, betAmount);
        GameState updatedGameState = gameService.placeBet(currentState, betAmount);

        gameStateService.saveGameState(updatedGameState);
        session.setAttribute("gameState", updatedGameState);

        return ResponseEntity.ok(ApiResponse.<GameStateBet>builder()
            .status("success")
            .gameState(buildBetDto(updatedGameState))
            .currentTokens(updatedPlayer.getTokens())
            .build());
    }

    @PostMapping("/retake_bet")
    public ResponseEntity<?> retakeBet(HttpSession session) {
        SessionContext context = authenticationService.validateAndGetContext(session);
        Player player = context.player();
        GameState currentState = context.gameStateOptional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "GAME_NOT_FOUND"));

        RetakeResult result = gameService.retakeBet(currentState);

        Player updatedPlayer = playerService.deductBet(player, result.amountReturned() * -1);

        gameStateService.saveGameState(result.newState());
        session.setAttribute("gameState", result.newState());

        return ResponseEntity.ok(ApiResponse.<GameStateBet>builder()
            .status("success")
            .gameState(buildBetDto(result.newState()))
            .currentTokens(updatedPlayer.getTokens())
            .build());
    }

    private GameStateBet buildBetDto(GameState state) {
        return GameStateBet.builder()
            .bet(state.getBet())
            .betList(state.getBetList())
            .deckLen(state.getDeckLen())
            .build();
    }
}
