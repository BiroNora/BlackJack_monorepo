package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.ApiResponse;
import com.blackjack.blackjack.dto.bet.GameStateBet;
import com.blackjack.blackjack.dto.init.InitializationRequest;
import com.blackjack.blackjack.dto.init.InitializationResponse;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.Player;
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
import com.blackjack.blackjack.dto.init.ForceRestartRequest;

import java.time.OffsetDateTime;
import java.util.Collections;
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

        // Kliens által küldött tartós azonosító (localStorage)
        UUID clientIdFromStorage = requestRecord.clientId();
        logger.info("  -> Frontend ID (localStorage/Request Body): {}", clientIdFromStorage);

        Player player = null;

        // --- 1. PRIOTITÁS: Keresés a Request Body-ban lévő ID alapján (TARTÓS ID) ---
        if (clientIdFromStorage != null) {
            Optional<Player> existingPlayer = playerRepository.findByClientId(clientIdFromStorage);

            if (existingPlayer.isPresent()) {
                player = existingPlayer.get();
                logger.info("1. Prioritás: Meglévő Player betöltve a tartós ID ({}) alapján.", clientIdFromStorage);

                // Frissítjük a Session ID-t az ÚJ/AKTÍV Session ID-ra
                player.setSessionId(currentSessionId);
                player.setLastActivity(OffsetDateTime.now());

            } else {
                // --- 2. ÚJ PLAYER LÉTREHOZÁSA (INSERT Logika) ---
                player = new Player();
                player.setClientId(clientIdFromStorage);

                logger.info("2. Prioritás: Új Player objektum létrehozva. Client ID beállítva: {}", player.getClientId());

                player.setSessionId(currentSessionId);
                player.setTokens(INITIAL_TOKENS);
                player.setLastActivity(OffsetDateTime.now());
                player.setIdempotencyKey(UUID.randomUUID());
            }
        }

        // --- BIZTOSÍTÉK: Ha a Frontend valamiért nem küldött Client ID-t ---
        if (player == null) {
            logger.error("SÚLYOS HIBA: A frontend nem küldött 'clientId'-t a request body-ban! Kényszerített új Client ID létrehozása.");
            // Létrehozunk egy új Player rekordot véletlenszerű ID-val, hogy ne törjön el az alkalmazás
            UUID forcedNewClientId = UUID.randomUUID();
            player = new Player();
            player.setClientId(forcedNewClientId);
            player.setSessionId(currentSessionId);
            player.setTokens(INITIAL_TOKENS);
            player.setLastActivity(OffsetDateTime.now());
            player.setIdempotencyKey(UUID.randomUUID());
        }


        // 3. Mentés/Frissítés az adatbázisban
        Player savedPlayer = playerRepository.save(player);
        logger.info("Player DB-ben frissítve/mentve. A rekord Client ID-ja: {}", savedPlayer.getClientId());

        // --- 🔑 KRITIKUS JAVÍTÁS: Client ID mentése az HttpSession-be (Redis) ---
        session.setAttribute("clientId", savedPlayer.getClientId().toString());
        logger.info("A Client ID ({}) sikeresen elmentve az HttpSession-be (Redis) 'clientId' néven.", savedPlayer.getClientId());

        // *** 🃏 HIÁNYZÓ LOGIKA BEILLESZTÉSE: A GameState Létrehozása és Mentése ***
        GameState initialGameState = new GameState(); // Feltételezve, hogy a konstruktor beállítja az alapértékeket (pl. deck len: 104, bet: 0, tokens: 1000)

        // MENTÉS: E NÉLKÜL a '/api/bet' nem találja a GameState objektumot!
        session.setAttribute("gameState", initialGameState);
        logger.info("A GameState objektum sikeresen elmentve az HttpSession-be 'gameState' néven.");
        // HttpOnly süti beállítása a tartós azonosításhoz (ha szükséges)
        setClientIdCookie(response, savedPlayer.getClientId());

        // 4. 🛡️ Spring Security Konfiguráció
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            savedPlayer.getClientId().toString(),
            null,
            Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 5. DTO Összeállítása
        InitializationResponse.GameStateInit gameStateData =
            InitializationResponse.GameStateInit.builder()
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
        if (clientIdString == null || clientIdString.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                ApiResponse.builder().status("error").gameStateHint("CLIENT_ID_REQUIRED").build()
            );
        }

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

        Player player = playerOptional.get();
        logger.info("Player betöltve a DB-ből. Jelenlegi session ID: {}", session.getId());


        // 2. KRITIKUS: Session és Security Konzisztencia Helyreállítása
        // Ha a kliens nem küldött érvényes Session ID-t (és a Spring új Session-t hozott létre),
        // kényszerítjük a DB-ből betöltött Player ID-jának az új Session-höz való hozzárendelését.

        // A. Spring Security Context beállítása a tartós azonosítóval
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            player.getClientId().toString(),
            null,
            Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // B. Session Attribútum Frissítése
        session.setAttribute("clientId", player.getClientId().toString());

        // 3. Játékállapot Resetelése (Session/Redis Tisztítás)

        // Töröljük a régi, potenciálisan hibás/inkonzisztens GameState-et a Session-ből
        session.removeAttribute("gameState");
        logger.info("Régi gameState eltávolítva a Session-ből.");

        // Létrehozzuk az új, tiszta játékállapotot
        GameState newGameState = new GameState();

        // MENTÉS: Ezt az új állapotot mentjük az aktuális HTTP sessionbe (és a Redisbe)
        session.setAttribute("gameState", newGameState);
        logger.info("Új, tiszta gameState objektum mentve a Session-be.");

        // 4. Player utolsó aktivitásának frissítése a DB-ben
        player.setSessionId(session.getId());
        player.setLastActivity(OffsetDateTime.now());
        Player savedPlayer = playerRepository.save(player); // DB frissítés

        // 5. Válasz összeállítása (tokent érintetlenül hagyjuk)
        GameStateBet gameStateData = GameStateBet.builder()
            .bet(newGameState.getBet())
            .betList(newGameState.getBetList())
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


    // --- Segédmetódusok ---
    private void setClientIdCookie(HttpServletResponse response, UUID clientId) {
        Cookie cookie = new Cookie(CLIENT_ID_COOKIE_NAME, clientId.toString());
        cookie.setMaxAge(365 * 24 * 60 * 60); // 1 year
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        response.addCookie(cookie);
        logger.info("Long-term cookie set: {}={}", CLIENT_ID_COOKIE_NAME, clientId);
    }
}
