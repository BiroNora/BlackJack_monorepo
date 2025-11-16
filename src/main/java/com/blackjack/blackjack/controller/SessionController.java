package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.init.InitializationResponse;
import com.blackjack.blackjack.dto.init.InitializationRequest;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.repository.PlayerRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
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
        HttpServletRequest request,
        HttpServletResponse response,
        HttpSession session) {

        String currentSessionId = session.getId();
        logger.info("Session inicializálás indult. Session ID: {}", currentSessionId);

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
            .message("Játék session sikeresen indult.")
            .gameState(gameStateData)
            .gameStateHint("Place your bet.")
            .tokens(savedPlayer.getTokens())
            .build());
    }

    // --- Segédmetódusok (változatlanul) ---

    // A getClientIdFromCookie metódust már nem használjuk a fő logikában, de a setCookie megmarad.
    private UUID getClientIdFromCookie(HttpServletRequest request) {
        // ... (kód változatlan)
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (CLIENT_ID_COOKIE_NAME.equals(cookie.getName())) {
                try {
                    return UUID.fromString(cookie.getValue());
                } catch (IllegalArgumentException e) {
                    logger.warn("Érvénytelen Client ID található a sütiben: {}", cookie.getValue());
                    return null;
                }
            }
        }
        return null;
    }

    private void setClientIdCookie(HttpServletResponse response, UUID clientId) {
        Cookie cookie = new Cookie(CLIENT_ID_COOKIE_NAME, clientId.toString());
        cookie.setMaxAge(365 * 24 * 60 * 60); // 1 év
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        response.addCookie(cookie);
        logger.info("Hosszú lejáratú süti beállítva: {}={}", CLIENT_ID_COOKIE_NAME, clientId);
    }
}
