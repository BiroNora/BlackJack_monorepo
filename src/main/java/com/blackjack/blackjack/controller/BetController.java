package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.ApiResponse;
import com.blackjack.blackjack.dto.SessionContext;
import com.blackjack.blackjack.dto.bet.BetRequest;
import com.blackjack.blackjack.dto.bet.GameStateBet;
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
        if (player.getTokens() < betAmount) {
            return ResponseEntity.badRequest().body("Nincs elegendő token a fogadáshoz.");
        }

        Player updatedPlayer;
        try {
            updatedPlayer = playerService.deductBetAndSave(player, betAmount);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Hiba történt a token levonása során.");
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
            updatedPlayer = playerService.deductBetAndSave(player, amountToReturn);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Hiba történt a tokenek visszaadása során.");
        }

        int updatedTokens = updatedPlayer.getTokens();

        GameState updatedGameState = gameStateService.getGameState(currentClientId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, "Nem sikerült újra betölteni a frissített játékállapotot."));

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
