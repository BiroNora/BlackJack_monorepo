package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.common.GameConstants;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class BetController {
    private static final Logger logger = LoggerFactory.getLogger(BetController.class);
    private final GameStateService gameStateService;
    private final PlayerService playerService;
    private final GameService gameService;
    private final StringRedisTemplate stringRedisTemplate;
    private final AuthenticationService authenticationService;
    private final RedisTemplate<String, Object> generalRedisTemplate;

    @Autowired
    public BetController(
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
        Optional<GameState> gameStateOptional = context.gameStateOptional();
        UUID currentClientId = context.clientId();
        Player player = context.player();

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
            deckLength = GameConstants.INITIAL_DECK_LENGTH;
        } else {
            deckLength = updatedGameState.getDeck().getDeckLength();
        }

        GameStateBet gameStateData = GameStateBet.builder()
            .bet(betAmount)
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
}
