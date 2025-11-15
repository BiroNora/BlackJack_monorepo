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

        String newSessionId = session.getId();
        logger.info("Session inicializálás indult. Új Session ID: {}", newSessionId);

        UUID clientIdFromCookie = getClientIdFromCookie(request);
        UUID clientIdFromStorage = requestRecord.clientId();
        logger.info("  -> Frontend ID (localStorage/Request Body): {}", clientIdFromStorage);

        Player player = null;

        // --- 1. PRIOTITÁS: Keresés az AKTÍV SESSION ID alapján (Ütközés elkerülése) ---
        // A tábla nevét feltételezzük: players
        logger.info("  -> 1. Prioritás keresés indult: Session ID: {}", newSessionId); // <-- KIÍRÁS

        Optional<Player> existingPlayerBySessionId = playerRepository.findBySessionId(newSessionId);

        if (existingPlayerBySessionId.isPresent()) {
            player = existingPlayerBySessionId.get();
            // 💡 KIÍRÁS: Ha találtunk Session ID alapján, ez a Player betöltődik!
            logger.warn("1. Prioritás: Rögzített Session ID-t találtunk a DB-ben. Betöltött Client ID: {}", player.getClientId());
            player.setLastActivity(OffsetDateTime.now());
        }

        // --- 2. PRIOTITÁS: Keresés a Request Body-ban lévő ID alapján (TARTÓS ID) ---
        if (player == null && clientIdFromStorage != null) {
            // Itt valószínűleg nem volt találat, ezért futott le az INSERT.
            Optional<Player> existingPlayerByStorageId = playerRepository.findByClientId(clientIdFromStorage);

            if (existingPlayerByStorageId.isPresent()) {
                player = existingPlayerByStorageId.get();
                logger.info("2. Prioritás: Meglévő Player betöltve a frontend által küldött ID (localStorage) alapján.");
                player.setSessionId(newSessionId);
                player.setLastActivity(OffsetDateTime.now());
                setClientIdCookie(response, player.getClientId());
            }
        }

        // --- 3. PRIOTITÁS: Keresés a Client ID (HttpOnly süti) alapján (BIZTONSÁGI TARTALÉK) ---
        if (player == null && clientIdFromCookie != null) {
            Optional<Player> existingPlayerByCookieId = playerRepository.findByClientId(clientIdFromCookie);

            if (existingPlayerByCookieId.isPresent()) {
                player = existingPlayerByCookieId.get();
                logger.info("3. Prioritás: Meglévő Player betöltve Client ID (süti) alapján.");
                player.setSessionId(newSessionId);
                player.setLastActivity(OffsetDateTime.now());
            }
        }

        // 💡 --- 4. ÚJ PLAYER LÉTREHOZÁSA (INSERT Logika) ---
        if (player == null) {

            if (clientIdFromStorage == null) {
                logger.error("SÚLYOS HIBA: A frontend nem küldött 'clientId'-t a request body-ban! Feltehetőleg belemószeroltak.");
                throw new IllegalArgumentException("Session inicializálási hiba: A tartós kliens azonosító (clientId) hiányzik.");
            }
            player = new Player();
            player.setClientId(clientIdFromStorage);

            logger.info("  -> 4. Prioritás: Új Player objektum létrehozva. Client ID beállítva: {}", player.getClientId()); // <-- KIÍRÁS: A frontend ID-ja

            player.setSessionId(newSessionId);
            player.setTokens(INITIAL_TOKENS);
            player.setLastActivity(OffsetDateTime.now());
            player.setIdempotencyKey(UUID.randomUUID());
            setClientIdCookie(response, clientIdFromStorage);
        }

        // 5. Mentés/Frissítés az adatbázisban
        // 💡 Fontos: Használjuk a save által visszaadott példányt a konzisztencia érdekében!
        Player savedPlayer = playerRepository.save(player);
        logger.info("Player DB-ben frissítve/mentve. A rekord Client ID-ja (Mentés utáni log): {}", savedPlayer.getClientId());

        // Frissítjük a referenciát
        player = savedPlayer;

        // 6. 🛡️ Spring Security Konfiguráció
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            player.getClientId().toString(),
            null,
            Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 7. DTO Összeállítása
        InitializationResponse.GameStateInit gameStateData =
            InitializationResponse.GameStateInit.builder()
                .deckLen(INITIAL_DECK_LENGTH)
                .build();

        return ResponseEntity.ok(InitializationResponse.builder()
            .status("success")
            .message("Játék session sikeresen indult.")
            .gameState(gameStateData)
            .gameStateHint("Place your bet.")
            .tokens(player.getTokens())
            .build());
    }

    // --- Segédmetódusok ---

    private UUID getClientIdFromCookie(HttpServletRequest request) {
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
