package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.ApiResponse;
import com.blackjack.blackjack.dto.bet.BetRequest;
import com.blackjack.blackjack.dto.bet.GameStateBet;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.PhaseState;
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

import static com.blackjack.blackjack.common.GameConstants.INITIAL_DECK_LENGTH;

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
        Player player = playerService.getAndValidatePlayer(betRequest.clientId());

        if (playerService.isDuplicateRequest(player, betRequest.idempotencyKey())) {
            return ResponseEntity.ok(createDuplicateResponse(player));
        }

        GameState currentState = player.getCurrentGameState();
        if (currentState == null) {
            currentState = GameState.builder()
                .isRoundActive(false)
                .build();
        }

        GameStateBet betDto = betService.placeBet(currentState, betRequest.bet());

        GameState updatedState = currentState.toBuilder()
            .bet(betDto.bet())
            .betList(betDto.betList())
            .isRoundActive(false)
            .targetPhase(betDto.targetPhase())
            .build();

        Player playerToSave = player.toBuilder()
            .currentGameState(updatedState)
            .idempotencyKey(betRequest.idempotencyKey())
            .build();

        Player savedPlayer = playerService.deductBet(playerToSave, betRequest.bet());

        return ResponseEntity.ok(ApiResponse.<GameStateBet>builder()
            .status("success")
            .gameState(betDto)
            .currentTokens(savedPlayer.getTokens())
            .gameStateHint("BET_SUCCESSFULLY_PLACED")
            .build());
    }

    @PostMapping("/retake_bet")
    public ResponseEntity<?> retakeBet(@RequestBody BetRequest betRequest) {
        // 1. Validálás és betöltés: Az AuthenticationService helyett a PlayerService-t használjuk
        Player player = playerService.getAndValidatePlayer(betRequest.clientId());

        // 2. Idempotencia ellenőrzés: Ne lehessen kétszer visszakérni ugyanazt a tétet
        if (playerService.isDuplicateRequest(player, betRequest.idempotencyKey())) {
            return ResponseEntity.ok(createDuplicateResponse(player));
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
                .targetPhase(betDto.targetPhase())
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
            .gameStateHint("BET_SUCCESSFULLY_RETAKEN")
            .build());
    }

    private ApiResponse<GameStateBet> createDuplicateResponse(Player player) {
        GameState state = player.getCurrentGameState();
        int dLen = (state != null && state.getDeck() != null) ? state.getDeck().getDeckLength() : INITIAL_DECK_LENGTH;

        GameStateBet currentBetDto = (state != null)
            ? new GameStateBet(state.getBet(), state.getBetList(), dLen, state.getTargetPhase())
            : null;

        return ApiResponse.<GameStateBet>builder()
            .status("success")
            .currentTokens(player.getTokens())
            .gameState(currentBetDto)
            .gameStateHint("BET_SUCCESS")
            .build();
    }
}
