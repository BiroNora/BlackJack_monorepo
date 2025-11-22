package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.ApiResponse;
import com.blackjack.blackjack.dto.SessionContext;
import com.blackjack.blackjack.dto.bet.GameStateBet;
import com.blackjack.blackjack.dto.start.GameStateStart;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.service.AuthenticationService;
import com.blackjack.blackjack.service.GameService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api")
public class GameController {
    private static final Logger logger = LoggerFactory.getLogger(GameController.class);
    private final GameService gameService;
    private final AuthenticationService authenticationService;

    public GameController(GameService gameService, AuthenticationService authenticationService
    ) {
        this.gameService = gameService;
        this.authenticationService = authenticationService;
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
}
