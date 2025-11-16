package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.common.GameConstants;
import com.blackjack.blackjack.dto.ApiResponse;
import com.blackjack.blackjack.dto.bet.BetRequest;
import com.blackjack.blackjack.dto.bet.GameStateBet;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.service.GameService;
import com.blackjack.blackjack.service.GameStateService;
import com.blackjack.blackjack.service.PlayerService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate; // <-- ÚJ IMPORT
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set; // <-- ÚJ IMPORT

@RestController
@RequestMapping("/api")
public class BetController {
    private final GameStateService gameStateService;
    private final PlayerService playerService;
    private final GameService gameService;
    private final StringRedisTemplate stringRedisTemplate;

    // 🔑 Redis Template az általános célú lekérdezésekhez (session diagnosztika)
    // Ezt injektálja be a generalRedisTemplate (String, Object) beanjével
    private final RedisTemplate<String, Object> generalRedisTemplate;

    private static final Logger logger = LoggerFactory.getLogger(BetController.class);

    @Autowired
    public BetController(
        GameStateService gameStateService,
        PlayerService playerService,
        GameService gameService, StringRedisTemplate stringRedisTemplate,
        // 🔑 ÚJ PARAMÉTER: A generalRedisTemplate injektálása
        RedisTemplate<String, Object> generalRedisTemplate) {
        this.gameStateService = gameStateService;
        this.playerService = playerService;
        this.gameService = gameService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.generalRedisTemplate = generalRedisTemplate;
    }

    @PostMapping("/bet")
    public ResponseEntity<?> placeBet(
        @RequestBody BetRequest betRequest,
        HttpSession session,
        @RequestHeader(value = "Cookie", required = false) String cookieHeader) {


        // 1. LOGOLÁS: Kérés beérkezett és Session adatok
        logger.info("--- Kérés beérkezett ---");
        logger.info("Teljes Cookie Header: {}", cookieHeader);
        String sessionId = session.getId();
        logger.info("Lekért Session ID (HttpSession.getId()): {}", sessionId);


        // 2. 🔑 HIBADIAGNOSZTIKA: Session adatok ellenőrzése a Redisben
        String fullSessionKey = "spring:session:sessions:" + sessionId;
        Boolean keyExists = generalRedisTemplate.hasKey(fullSessionKey);

        logger.info("--- REDIS DIAGNOSZTIKA ---");
        logger.info("A munkamenet kulcs ({}) létezik a Redisben: {}", fullSessionKey, keyExists);

        if (Boolean.TRUE.equals(keyExists)) {
            try {
                // Lekérdezzük a Client ID-t közvetlenül a session attribútumok közül
                Object clientIdAttr = generalRedisTemplate.opsForHash().get(fullSessionKey, "sessionAttr:clientId");
                logger.info("A Redisben tárolt 'clientId' attribútum értéke: {}", clientIdAttr);

            } catch (Exception e) {
                logger.error("Hiba történt a Redis session tartalmának lekérésekor", e);
            }
        }
        logger.info("-------------------------");


        // 3. ❌ HIBÁSAN KEZELT AZONOSÍTÓ JAVÍTÁSA:
        // A Client ID-t az HttpSession-ből kell kiolvasni, nem az Session ID-ból generálni.
        Object clientIdObj = session.getAttribute("clientId");

        if (clientIdObj == null) {
            // EZ AZ OKA A 403-AS HIBÁNAK: Nincs Client ID a sessionben!
            // Ez azt jelenti, hogy a /initialize_session vagy nem futott le, vagy a session elveszett.
            logger.warn("HIBÁS ÁLLAPOT: A 'clientId' attribútum hiányzik az HttpSession-ből. sessionID: {}", sessionId);
            // Visszaadjuk a 403-at (vagy 401-et), mert a felhasználó nem azonosítható.
            return ResponseEntity.status(403).body("A munkamenet érvénytelen vagy nem inicializált. Hiányzik a Client ID.");
        }

        UUID currentClientId;
        try {
            currentClientId = UUID.fromString(clientIdObj.toString());
        } catch (IllegalArgumentException e) {
            logger.error("A sessionben tárolt 'clientId' formátuma érvénytelen: {}", clientIdObj);
            return ResponseEntity.status(403).body("A belső ügyfélazonosító érvénytelen formátumú.");
        }

        // 4. GameState lekérése és Logika
        Optional<GameState> gameStateOptional = gameStateService.getGameState(currentClientId);

        // --- A TÖBBI KÓD VÁLTOZATLANUL MARAD ---
        Optional<Player> playerOptional = playerService.getPlayerByClientId(currentClientId);

        if (playerOptional.isEmpty()) {
            return ResponseEntity.status(404).body("Játékos nem található.");
        }

        Player player = playerOptional.get();
        int betAmount = betRequest.betAmount();

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
            .gameStateHint("A laposztás a tét után történik.")
            .tokens(updatedTokens)
            .build());
    }

    @GetMapping("/debug/raw-session/{sessionId}")
    public Map<Object, Object> debugRawRedisSession(@PathVariable String sessionId) {
        String fullSessionKey = "spring:session:sessions:" + sessionId;
        logger.info(">>> REDIS NYERS ADAT DIAGNOSZTIKA HÍVÁS: {}", fullSessionKey);

        // A StringRedisTemplate használatával kikerüljük a Jackson deserializálást.
        Map<Object, Object> rawData = stringRedisTemplate.opsForHash().entries(fullSessionKey);

        if (rawData.isEmpty()) {
            logger.warn("!!! HIBA: A session kulcs ({}) nem található vagy üres.", fullSessionKey);
        } else {
            logger.info("--- Talált session kulcsok és nyers adatok ---");
            rawData.forEach((k, v) -> {
                logger.info("  HASH KULCS: {} | NYERS ÉRTÉK (STRING): {}", k, v);
            });
            logger.info("--- Diagnosztika vége ---");
        }
        return rawData;
    }
}
