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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static com.blackjack.blackjack.common.GameConstants.INITIAL_DECK_LENGTH;

@RestController
@RequestMapping("/api")
public class BetAndDeckController {
    private static final Logger logger = LoggerFactory.getLogger(BetAndDeckController.class);
    private final GameStateService gameStateService;
    private final PlayerService playerService;
    private final GameService gameService;
    private final StringRedisTemplate stringRedisTemplate;
    private final AuthenticationService authenticationService;
    private final RedisTemplate<String, Object> generalRedisTemplate;

    @Autowired
    public BetAndDeckController(
        GameStateService gameStateService,
        PlayerService playerService,
        GameService gameService, StringRedisTemplate stringRedisTemplate, AuthenticationService authenticationService,

        RedisTemplate<String, Object> generalRedisTemplate) {
        this.gameStateService = gameStateService;
        this.playerService = playerService;
        this.gameService = gameService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.authenticationService = authenticationService;
        this.generalRedisTemplate = generalRedisTemplate;
    }

    @PostMapping("/bet")
    public ResponseEntity<?> placeBet(
        @RequestBody BetRequest betRequest,
        HttpSession session,
        @RequestHeader(value = "Cookie", required = false) String cookieHeader) {

        SessionContext context = authenticationService.validateAndGetContext(session);

        UUID currentClientId = context.clientId();
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

        GameState updatedGameState = gameService.placeBet(gameStateOptional, currentClientId, betAmount);

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
            .message("Tét sikeresen elhelyezve. Kérem, várja az laposztást.")
            .gameState(gameStateData)
            .gameStateHint("BET_SUCCESSFULLY_PLACED")
            .currentTokens(updatedTokens)
            .build());
    }

    @PostMapping("/retake_bet")
    public ResponseEntity<?> retakeBet(
        HttpSession session,
        @RequestHeader(value = "Cookie", required = false) String cookieHeader) {

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
            .message("Tét sikeresen visszavéve.")
            .gameState(gameStateData)
            .gameStateHint("BET_SUCCESSFULLY_RETRAKEN")
            .currentTokens(updatedTokens)
            .build());
    }

    @PostMapping("/create_deck")
    public ResponseEntity<?> createDeck(
        HttpSession session,
        @RequestHeader(value = "Cookie", required = false) String cookieHeader) {

        SessionContext context = authenticationService.validateAndGetContext(session);

        UUID currentClientId = context.clientId();

        Player player = context.player();
        int tokens = player.getTokens();

        Optional<GameState> gameStateOptional = context.gameStateOptional();
        GameState updatedGameState = gameService.createNewDeck(gameStateOptional);

        int deckLength = updatedGameState.getDeck() != null ? updatedGameState.getDeck().getDeckLength() : 0;

        GameStateBet gameStateData = GameStateBet.builder()
            .bet(updatedGameState.getBet())
            .betList(updatedGameState.getBetList())
            .deckLen(deckLength)
            .build();

        return ResponseEntity.ok(ApiResponse.<GameStateBet>builder()
            .status("success")
            .message("Új pakli sikeresen létrehozva.")
            .gameState(gameStateData)
            .gameStateHint("DECK_CREATED")
            .currentTokens(tokens)
            .build());
    }
}
