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
        GameState currentState = context.gameStateOptional().orElseThrow();

        // 1. DTO generálása
        GameStateBet betDto = gameService.placeBet(currentState, betRequest.bet());

        // 2. Szerver oldali állapot frissítése (Hogy a deckLen ne vesszen el a DB-ből)
        GameState updatedGameState = currentState.toBuilder()
            .bet(betDto.bet())
            .betList(betDto.betList())
            .build();

        Player updatedPlayer = playerService.deductBet(context.player(), betRequest.bet());
        gameStateService.saveGameState(updatedGameState);
        session.setAttribute("gameState", updatedGameState);

        return ResponseEntity.ok(ApiResponse.<GameStateBet>builder()
            .status("success")
            .gameState(betDto) // A frontend megkapja a tétet és a deckLen-t
            .currentTokens(updatedPlayer.getTokens())
            .build());
    }

    @PostMapping("/retake_bet")
    public ResponseEntity<?> retakeBet(HttpSession session) {
        SessionContext context = authenticationService.validateAndGetContext(session);
        GameState currentState = context.gameStateOptional().orElseThrow();

        RetakeResult result = gameService.retakeBet(currentState);
        GameStateBet betDto = result.newState();

        GameState updatedGameState = currentState.toBuilder()
            .bet(betDto.bet())
            .betList(betDto.betList())
            .isRoundActive(!betDto.betList().isEmpty())
            .build();

        Player updatedPlayer = playerService.deductBet(context.player(), -result.amountReturned());
        gameStateService.saveGameState(updatedGameState);
        session.setAttribute("gameState", updatedGameState);

        return ResponseEntity.ok(ApiResponse.<GameStateBet>builder()
            .status("success")
            .gameState(betDto)
            .currentTokens(updatedPlayer.getTokens())
            .build());
    }
}
