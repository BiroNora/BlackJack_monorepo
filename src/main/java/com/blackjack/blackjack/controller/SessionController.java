package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.repository.PlayerRepository;
import com.blackjack.blackjack.dto.InitializationResponse;
import com.blackjack.blackjack.dto.InitializationResponse.GameStateInit; // 💡 Javított import beágyazott osztályhoz
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static com.blackjack.blackjack.common.GameConstants.INITIAL_DECK_LENGTH;

@RestController
@RequestMapping("/api")
public class SessionController {

    private static final Logger logger = LoggerFactory.getLogger(SessionController.class);
    private final PlayerRepository playerRepository;

    private static final String CLIENT_ID_COOKIE_NAME = "clientId";
    private static final int INITIAL_TOKENS = 1000;

    public SessionController(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @PostMapping("/initialize_session")
    public ResponseEntity<InitializationResponse> initializeSession(
        HttpServletRequest request,
        HttpServletResponse response,
        HttpSession session) {

        String newSessionId = session.getId();
        logger.info("Session inicializálás indult. Új Session ID: {}", newSessionId);

        UUID clientId = getClientIdFromCookie(request);
        Player player = null;

        // --- 1. PRIOTITÁS: Keresés az AKTÍV SESSION ID alapján (UPDATE Logika) ---
        // Ez kezeli azt az esetet, ha a session_id már létezik a DB-ben.
        // Ha találunk meglévő rekordot, azt UPDATE-eljük INSERT helyett.
        Optional<Player> existingPlayerBySessionId = playerRepository.findBySessionId(newSessionId);

        if (existingPlayerBySessionId.isPresent()) {
            player = existingPlayerBySessionId.get();
            logger.warn("Rögzített Session ID-t találtunk a DB-ben. Frissítjük a meglévő Player rekordot. Client ID: {}", player.getClientId());

            // UPDATE: Frissítjük az aktivitást
            player.setLastActivity(OffsetDateTime.now());
        }

        // --- 2. PRIOTITÁS: Keresés a Client ID (süti) alapján (HA AZ 1. NEM TALÁLT) ---
        if (player == null && clientId != null) {
            Optional<Player> existingPlayerByClientId = playerRepository.findByClientId(clientId);

            if (existingPlayerByClientId.isPresent()) {
                player = existingPlayerByClientId.get();
                logger.info("Meglévő Player betöltve Client ID alapján: {}", player.getClientId());

                // 🔑 Frissítjük a Session ID-t és az aktivitást (UPDATE lesz)
                player.setSessionId(newSessionId);
                player.setLastActivity(OffsetDateTime.now());
            }
        }

        // --- 3. ÚJ PLAYER LÉTREHOZÁSA (INSERT Logika) ---
        if (player == null) {
            clientId = UUID.randomUUID();
            player = new Player();
            player.setClientId(clientId);
            player.setSessionId(newSessionId);
            player.setTokens(INITIAL_TOKENS);
            player.setLastActivity(OffsetDateTime.now());
            player.setIdempotencyKey(UUID.randomUUID());

            logger.info("Új Player létrehozva, ID: {}", clientId);

            // Új hosszú lejáratú süti beállítása
            setClientIdCookie(response, clientId);
        }

        // 4. Mentés/Frissítés az adatbázisban (SAVE)
        // Az ORM (Hibernate) eldönti, hogy INSERT vagy UPDATE kell.
        playerRepository.save(player);
        logger.info("Player DB-ben frissítve/mentve. Session ID: {}", newSessionId);

        // 5. 🛡️ Spring Security Konfiguráció
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            player.getClientId().toString(),
            null,
            Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        logger.debug("Spring Security kontextus beállítva a(z) {} ID-ra.", player.getClientId());

        // 7. DTO Összeállítása
        InitializationResponse.GameStateInit gameStateData =
            InitializationResponse.GameStateInit.builder()
                .deckLen(INITIAL_DECK_LENGTH)
                .build();

        // 💡 Javított visszatérési érték (ResponseEntity.ok() hozzáadva)
        return ResponseEntity.ok(InitializationResponse.builder()
            .status("success")
            .message("Játék session sikeresen indult.")
            .gameState(gameStateData)
            .gameStateHint("Place your bet.")
            .tokens(player.getTokens())
            .build());

    }

    // --- Segédmetódusok ---

    /**
     * Lekéri a clientId-t a sütik közül.
     */
    private UUID getClientIdFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
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

    /**
     * Beállítja a hosszú lejáratú clientId sütit.
     */
    private void setClientIdCookie(HttpServletResponse response, UUID clientId) {
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(CLIENT_ID_COOKIE_NAME, clientId.toString());
        cookie.setMaxAge(365 * 24 * 60 * 60); // 1 év
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        response.addCookie(cookie);
        logger.info("Hosszú lejáratú süti beállítva: {}={}", CLIENT_ID_COOKIE_NAME, clientId);
    }
}
