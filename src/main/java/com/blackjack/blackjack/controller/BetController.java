package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.ApiResponse;
import com.blackjack.blackjack.dto.bet.BetRequest;
import com.blackjack.blackjack.dto.bet.GameStateBet;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.service.BetService;
import com.blackjack.blackjack.service.BetService.RetakeResult;
import com.blackjack.blackjack.service.PlayerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BetController {
    private final PlayerService playerService;
    private final BetService betService;

    @Autowired
    public BetController(
        PlayerService playerService,
        BetService betService) {
        this.playerService = playerService;
        this.betService = betService;
    }

    @PostMapping("/bet")
    public ResponseEntity<?> placeBet(@RequestBody BetRequest betRequest) {
        // 1. Validálás (Auth helyett): Csak létező játékos fogadhat
        Player player = playerService.getAndValidatePlayer(betRequest.clientId());

        // 2. Duplikáció szűrés + Állapot visszaadása
        if (playerService.isDuplicateRequest(player, betRequest.idempotencyKey())) {
            GameState state = player.getCurrentGameState();
            assert state.getDeck() != null;
            return ResponseEntity.ok(ApiResponse.<GameStateBet>builder()
                .status("success")
                .currentTokens(player.getTokens())
                .gameState(new GameStateBet(state.getBet(), state.getBetList(), state.getDeck().getDeckLength())) // Visszaadjuk a mentett állapotot is
                .build());
        }

        // 3. Jelenlegi állapot kezelése
        GameState currentState = player.getCurrentGameState();
        if (currentState == null) {
            currentState = GameState.builder()
                .isRoundActive(false)
                .build();
        }

        // 4. Üzleti logika
        GameStateBet betDto = betService.placeBet(currentState, betRequest.bet());

        GameState updatedState = currentState.toBuilder()
            .bet(betDto.bet())
            .betList(betDto.betList())
            .isRoundActive(false)
            .build();

        Player playerToSave = player.toBuilder()
            .currentGameState(updatedState)
            .idempotencyKey(betRequest.idempotencyKey())
            .build();

        // 6. Mentés és levonás
        Player savedPlayer = playerService.deductBet(playerToSave, betRequest.bet());

        return ResponseEntity.ok(ApiResponse.<GameStateBet>builder()
            .status("success")
            .gameState(betDto)
            .currentTokens(savedPlayer.getTokens())
            .build());
    }

    @PostMapping("/retake_bet")
    public ResponseEntity<?> retakeBet(@RequestBody BetRequest betRequest) {
        // 1. Validálás és betöltés: Az AuthenticationService helyett a PlayerService-t használjuk
        Player player = playerService.getAndValidatePlayer(betRequest.clientId());

        // 2. Idempotencia ellenőrzés: Ne lehessen kétszer visszakérni ugyanazt a tétet
        if (playerService.isDuplicateRequest(player, betRequest.idempotencyKey())) {
            GameState state = player.getCurrentGameState();
            GameStateBet currentBetDto;
            if ((state != null)) {
                assert state.getDeck() != null;
                currentBetDto = new GameStateBet(state.getBet(), state.getBetList(), state.getDeck().getDeckLength());
            } else {
                currentBetDto = null;
            }

            return ResponseEntity.ok(ApiResponse.<GameStateBet>builder()
                .status("success")
                .currentTokens(player.getTokens())
                .gameState(currentBetDto)
                .build());
        }

        // 3. Jelenlegi állapot kinyerése
        GameState currentState = player.getCurrentGameState();
        if (currentState == null) {
            currentState = GameState.builder()
                .isRoundActive(false)
                .build();
        }

        // 4. Üzleti logika: Tét visszavétele
        RetakeResult result = betService.retakeBet(currentState);
        GameStateBet betDto = result.newState();

        // 5. Player előkészítése (JSON frissítés + új kulcs mentése)
        Player playerToSave = player.toBuilder()
            .currentGameState(currentState.toBuilder()
                .bet(betDto.bet())
                .betList(betDto.betList())
                .build())
            .idempotencyKey(betRequest.idempotencyKey())
            .build();

        // 6. Tokenek visszajuttatása és mentés egyetlen lépésben
        // A PlayerService.updateTokens elvégzi a mentést is!
        Player savedPlayer = playerService.updateTokens(playerToSave, result.amountReturned());

        return ResponseEntity.ok(ApiResponse.<GameStateBet>builder()
            .status("success")
            .gameState(betDto)
            .currentTokens(savedPlayer.getTokens())
            .build());
    }
}
