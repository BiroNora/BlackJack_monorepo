package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.ApiResponse;
import com.blackjack.blackjack.dto.bet.GameStateBet;
import com.blackjack.blackjack.dto.init.ForceRestartRequest;
import com.blackjack.blackjack.dto.init.InitializationRequest;
import com.blackjack.blackjack.dto.init.InitializationResponse;
import com.blackjack.blackjack.model.DealerHandMasked;
import com.blackjack.blackjack.model.DealerHandUnmasked;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.model.PlayerHand;
import com.blackjack.blackjack.repository.PlayerRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.blackjack.blackjack.common.GameConstants.INITIAL_DECK_LENGTH;
import static com.blackjack.blackjack.common.GameConstants.INITIAL_TOKENS;

@RestController
@RequestMapping("/api")
public class SessionController {

    private static final Logger logger = LoggerFactory.getLogger(SessionController.class);
    private static final String CLIENT_ID_COOKIE_NAME = "clientId";
    private final PlayerRepository playerRepository;

    public SessionController(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @PostMapping("/initialize_session")
    public ResponseEntity<InitializationResponse> initializeSession(
        @RequestBody InitializationRequest requestRecord,
        HttpServletResponse response,
        HttpSession session) {

        String currentSessionId = session.getId();
        logger.info("Session initialialization started. Session ID: {}", currentSessionId);

        UUID clientIdFromStorage = requestRecord.clientId();
        logger.info("  -> Frontend ID (localStorage/Request Body): {}", clientIdFromStorage);

        Player playerToSave;

        // --- 1. PRIOTITÁS: Keresés a Request Body-ban lévő ID alapján (TARTÓS ID) ---
        if (clientIdFromStorage != null) {
            Optional<Player> existingPlayer = playerRepository.findByClientId(clientIdFromStorage);

            if (existingPlayer.isPresent()) {
                Player existing = existingPlayer.get();
                logger.info("1. Prioritás: Meglévő Player betöltve a tartós ID ({}) alapján.", clientIdFromStorage);

                playerToSave = existing.toBuilder()
                    .sessionId(currentSessionId)
                    .lastActivity(OffsetDateTime.now())
                    .build();

            } else {
                // --- 2. ÚJ PLAYER LÉTREHOZÁSA (INSERT Logika) ---
                playerToSave = Player.builder()
                    .clientId(clientIdFromStorage)
                    .sessionId(currentSessionId)
                    .tokens(INITIAL_TOKENS)
                    .lastActivity(OffsetDateTime.now())
                    .idempotencyKey(UUID.randomUUID())
                    .build();

                logger.info("2. Prioritás: Új Player objektum létrehozva. Client ID beállítva: {}", playerToSave.getClientId());
            }
        } else {
            // --- BIZTOSÍTÉK: Ha a Frontend valamiért nem küldött Client ID-t ---
            logger.error("SÚLYOS HIBA: A frontend nem küldött 'clientId'-t a request body-ban! Kényszerített új Client ID létrehozása.");

            UUID forcedNewClientId = UUID.randomUUID();

            playerToSave = Player.builder()
                .clientId(forcedNewClientId)
                .sessionId(currentSessionId)
                .tokens(INITIAL_TOKENS)
                .lastActivity(OffsetDateTime.now())
                .idempotencyKey(UUID.randomUUID())
                .build();
        }


        // 3. Mentés/Frissítés az adatbázisban
        Player savedPlayer = playerRepository.save(playerToSave);
        logger.info("Player DB-ben frissítve/mentve. A rekord Client ID-ja: {}", savedPlayer.getClientId());

        session.setAttribute("clientId", savedPlayer.getClientId().toString());
        logger.info("A Client ID ({}) sikeresen elmentve az HttpSession-be (Redis) 'clientId' néven.", savedPlayer.getClientId());

        // *** 🃏 KRITIKUS JAVÍTÁS: A GameState Létrehozása a Builderrel ***

        PlayerHand initialPlayerHand = PlayerHand.createEmptyHand();
        DealerHandMasked initialDealerMasked = DealerHandMasked.builder().build();
        DealerHandUnmasked initialDealerUnmasked = DealerHandUnmasked.createEmptyHand();

        Map<String, PlayerHand> initialPlayersMap = new LinkedHashMap<>();
        initialPlayersMap.put(savedPlayer.getClientId().toString(), initialPlayerHand);

        GameState initialGameState = GameState.builder()
            .clientId(savedPlayer.getClientId())
            .sessionId(currentSessionId)
            .bet(0)
            .betList(Collections.emptyList())
            .isRoundActive(false)
            .player(initialPlayerHand)
            .dealerMasked(initialDealerMasked)
            .dealerUnmasked(initialDealerUnmasked)
            .players(initialPlayersMap)
            .build();

        session.setAttribute("gameState", initialGameState);
        logger.info("A GameState objektum sikeresen elmentve az HttpSession-be 'gameState' néven.");
        setClientIdCookie(response, savedPlayer.getClientId());

        // 4. 🛡️ Spring Security Konfiguráció (Változatlan)
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            savedPlayer.getClientId().toString(),
            null,
            Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 5. DTO Összeállítása
        InitializationResponse.GameStateInit gameStateData =
            InitializationResponse.GameStateInit.builder()
                // VISSZAÁLLÍTVA: A GameConstants konstans értékét használjuk, nem a GameState.getDeckLen()-t
                .deckLen(INITIAL_DECK_LENGTH)
                .build();

        return ResponseEntity.ok(InitializationResponse.builder()
            .status("success")
            .message("success.")
            .gameState(gameStateData)
            .gameStateHint("Place your bet.")
            .tokens(savedPlayer.getTokens())
            .build());
    }

    @PostMapping("/force_restart")
    public ResponseEntity<?> forceRestart(
        @RequestBody ForceRestartRequest request,
        HttpSession session) {

        String clientIdString = request.clientId();
        logger.info("Force Restart indítva kliens ID alapján: {}", clientIdString);

        // 0. Validálás
        UUID clientId;
        try {
            clientId = UUID.fromString(clientIdString);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                ApiResponse.builder().status("error").gameStateHint("INVALID_CLIENT_ID_FORMAT").build()
            );
        }

        // 1. Player betöltése a DB-ből (Tartós azonosítás)
        Optional<Player> playerOptional = playerRepository.findByClientId(clientId);

        if (playerOptional.isEmpty()) {
            logger.warn("Force Restart sikertelen: Játékos nem található Client ID: {}", clientIdString);
            return ResponseEntity.status(404).body(
                ApiResponse.builder().status("error").gameStateHint("USER_NOT_FOUND").build()
            );
        }

        Player oldPlayer = playerOptional.get();
        logger.info("Player betöltve a DB-ből. Jelenlegi session ID: {}", session.getId());


        // 2. KRITIKUS: Session és Security Konzisztencia Helyreállítása (Változatlan)
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            oldPlayer.getClientId().toString(),
            null,
            Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        session.setAttribute("clientId", oldPlayer.getClientId().toString());

        // 3. Játékállapot Resetelése (Session/Redis Tisztítás)
        session.removeAttribute("gameState");
        logger.info("Régi gameState eltávolítva a Session-ből.");

        // *** JAVÍTÁS: A GameState újrainicializálása ***

        PlayerHand initialPlayerHand = PlayerHand.createEmptyHand();
        DealerHandMasked initialDealerMasked = DealerHandMasked.builder().build();
        DealerHandUnmasked initialDealerUnmasked = DealerHandUnmasked.createEmptyHand();

        Map<String, PlayerHand> newPlayersMap = new LinkedHashMap<>();
        newPlayersMap.put(oldPlayer.getClientId().toString(), initialPlayerHand);

        GameState newGameState = GameState.builder()
            .clientId(oldPlayer.getClientId())
            .sessionId(session.getId())
            .bet(0)
            .betList(Collections.emptyList())
            .isRoundActive(false)
            .player(initialPlayerHand)
            .dealerMasked(initialDealerMasked)
            .dealerUnmasked(initialDealerUnmasked)
            .players(newPlayersMap)
            .build();

        session.setAttribute("gameState", newGameState);
        logger.info("Új, tiszta gameState objektum mentve a Session-be.");

        // 4. Player utolsó aktivitásának frissítése a DB-ben
        Player updatedPlayer = oldPlayer.toBuilder()
            .sessionId(session.getId())
            .lastActivity(OffsetDateTime.now())
            .build();

        Player savedPlayer = playerRepository.save(updatedPlayer); // DB frissítés

        // 5. Válasz összeállítása
        GameStateBet gameStateData = GameStateBet.builder()
            .bet(newGameState.getBet())
            .betList(newGameState.getBetList())
            // VISSZAÁLLÍTVA: A GameConstants konstans értékét használjuk
            .deckLen(INITIAL_DECK_LENGTH)
            .build();

        return ResponseEntity.ok(
            ApiResponse.builder()
                .status("success")
                .gameState(gameStateData)
                .gameStateHint("FORCE_RESTART_SUCCESSFUL_RESET_STATE")
                .currentTokens(savedPlayer.getTokens())
                .build()
        );
    }


    // --- Segédmetódusok (Változatlan) ---
    private void setClientIdCookie(HttpServletResponse response, UUID clientId) {
        Cookie cookie = new Cookie(CLIENT_ID_COOKIE_NAME, clientId.toString());
        cookie.setMaxAge(365 * 24 * 60 * 60); // 1 year
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        response.addCookie(cookie);
        logger.info("Long-term cookie set: {}={}", CLIENT_ID_COOKIE_NAME, clientId);
    }
}
