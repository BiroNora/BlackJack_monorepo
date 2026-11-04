package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.ActionRequest;
import com.blackjack.blackjack.dto.ApiResponse;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.repository.PlayerRepository;
import com.blackjack.blackjack.service.PlayerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

@Component
public class ControllerHelper {
    private final PlayerService playerService;
    private final PlayerRepository playerRepository;

    public ControllerHelper(PlayerService playerService, PlayerRepository playerRepository) {
        this.playerService = playerService;
        this.playerRepository = playerRepository;
    }

    /**
     * PlayerController Központi biztonsági ellenőrzés: megnézi, hogy a bejelentkezett Principal
     * megegyezik-e a request-ben küldött clientId-val.
     */
    public UUID validateAndGetClientId(Map<String, String> body, Principal principal) {
        // 1. Meglévő formátum és létezés ellenőrzés
        String clientIdStr = body.get("clientId");
        if (clientIdStr == null || clientIdStr.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CLIENT_ID_REQUIRED");
        }

        UUID clientId;
        try {
            clientId = UUID.fromString(clientIdStr);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_UUID_FORMAT");
        }

        // 2. Új biztonsági ellenőrzés (Principal vs Request clientId)
        if (principal == null || principal.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized access");
        }

        if (!principal.getName().equals(clientIdStr)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized client ID access");
        }

        // 3. Ha minden valid, visszaadjuk a konvertált UUID-t
        return clientId;
    }

    /**
     * Központi biztonsági ellenőrzés: megnézi, hogy a bejelentkezett Principal
     * megegyezik-e a request-ben küldött clientId-val.
     */
    public void validateClientAuthorization(Principal principal, String requestClientId) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized access");
        }

        String authenticatedClientId = principal.getName();

        if (authenticatedClientId == null || !authenticatedClientId.equals(requestClientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized client ID access");
        }
    }

    /**
     * Ellenőrzi a target_user_id-t.
     * - Ha hiba van, egy ResponseEntity.ok(...) ad vissza (IC-vel).
     * - Ha minden oké, magát a Player objektumot adja vissza.
     */
    public Object validateTargetUserIdSafely(String targetUserIdStr) {
        if (targetUserIdStr == null || targetUserIdStr.isBlank()) {
            return ResponseEntity.ok(Map.of("status", "IC"));
        }

        UUID target_user_id;
        try {
            target_user_id = UUID.fromString(targetUserIdStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(Map.of("status", "IC"));
        }

        Optional<Player> targetUserOpt = playerRepository.findByClientId(target_user_id);
        if (targetUserOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("status", "IC"));
        }

        return targetUserOpt.get();
    }

    /**
     * GENERIKUS KERETRENDSZER (DRY)
     * Ez a metódus kezeli a validálást, idempotenciát, mentést és DTO konverziót.
     */
    public <T> ResponseEntity<?> handleAction(
        ActionRequest request,
        Principal principal,
        String hint,
        Function<GameState, T> dtoBuilder,
        UnaryOperator<GameState> action,
        Integer tokenChange
    ) {
        validateClientAuthorization(principal, String.valueOf(request.getClientId()));
        Player player = playerService.getAndValidatePlayer(request.getClientId());

        // 1. Idempotencia ellenőrzés
        if (playerService.isDuplicateRequest(player, request.getIdempotencyKey())) {
            return ResponseEntity.ok(ApiResponse.builder()
                .status("success")
                .currentTokens(player.getTokens())
                .isGuest(player.isGuest())
                .gameState(dtoBuilder.apply(player.getCurrentGameState()))
                .build());
        }

        GameState current = player.getCurrentGameState();
        if (current == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active game state");

        // 2. Üzleti logika futtatása
        GameState updated = action.apply(current);

        // 3. Mentés előkészítése
        Player playerToSave = player.toBuilder()
            .currentGameState(updated)
            .idempotencyKey(request.getIdempotencyKey())
            .build();

        // 4. Atomi mentés (Token változással vagy anélkül)
        Player savedPlayer = (tokenChange != null && tokenChange != 0)
            ? playerService.updateTokens(playerToSave, tokenChange)
            : playerService.savePlayer(playerToSave);

        // 6. Válasz küldése
        return ResponseEntity.ok(ApiResponse.builder()
            .status("success")
            .gameState(dtoBuilder.apply(updated))
            .gameStateHint(hint)
            .currentTokens(savedPlayer.getTokens())
            .isGuest(savedPlayer.isGuest())
            .build());
    }
}
