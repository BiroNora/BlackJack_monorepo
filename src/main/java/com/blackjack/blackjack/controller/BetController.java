package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.ApiResponse;
import com.blackjack.blackjack.dto.SessionContext;
import com.blackjack.blackjack.dto.bet.BetRequest;
import com.blackjack.blackjack.dto.bet.GameStateBet;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.service.*;
import com.blackjack.blackjack.service.BetService.RetakeResult;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BetController {
    private static final Logger logger = LoggerFactory.getLogger(GameService.class);
    private final GameStateService gameStateService;
    private final PlayerService playerService;
    private final GameService gameService;
    private final AuthenticationService authenticationService;
    private final BetService betService;

    @Autowired
    public BetController(
        GameStateService gameStateService,
        PlayerService playerService,
        GameService gameService, AuthenticationService authenticationService, BetService betService) {
        this.gameStateService = gameStateService;
        this.playerService = playerService;
        this.gameService = gameService;
        this.authenticationService = authenticationService;
        this.betService = betService;
    }

    @PostMapping("/bet")
    public ResponseEntity<?> placeBet(@RequestBody BetRequest betRequest, HttpSession session) {
        SessionContext context = authenticationService.validateAndGetContext(session);
        GameState currentState = context.gameStateOptional().orElseThrow();

        // 1. DTO generálása
        GameStateBet betDto = betService.placeBet(currentState, betRequest.bet());

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

        RetakeResult result = betService.retakeBet(currentState);
        GameStateBet betDto = result.newState();

        GameState updatedGameState = currentState.toBuilder()
            .bet(betDto.bet())
            .betList(betDto.betList())
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
