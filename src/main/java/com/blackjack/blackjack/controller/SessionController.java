package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.GameStateInit;
import com.blackjack.blackjack.dto.SessionInitResponse;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.repository.PlayerRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.blackjack.blackjack.common.GameConstants.INITIAL_DECK_LENGTH;
import static com.blackjack.blackjack.common.GameConstants.INITIAL_TOKENS;

@RestController
@RequestMapping("/api")
public class SessionController {
    private static final String PLAYER_ID_SESSION_KEY = "clientId";

    private final PlayerRepository playerRepository;

    public SessionController(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    // A @Transactional segít abban, hogy a DB-műveletek szinkronizálva legyenek a session beállítás előtt.
    @PostMapping("/initialize_session")
    @Transactional
    public SessionInitResponse initializeSession(HttpSession session) {

        UUID existingClientId = (UUID) session.getAttribute(PLAYER_ID_SESSION_KEY);
        Player player;
        if (existingClientId != null) {
            // 1. ÁG: Játékos ID van a Sessionben (Redis/Cookie). Megbízunk benne.
            player = playerRepository.findById(existingClientId)
                .orElseThrow(() -> new RuntimeException("A session ID-hez tartozó játékos hiányzik!"));
        } else {
            // 2. ÁG: Új játékos / Strict Mode hívás.
            String currentHttpSessionId = session.getId();

            // 2.1. Próbáljuk megkeresni a Player-t a DB-ben a HTTP Session ID alapján (ha már mentve lett).
            player = playerRepository.findBySessionId(currentHttpSessionId);

            if (player == null) {
                // Tényleg új játékos (vagy a Strict Mode 1. kérése).

                // 🔒 HIBATŰRŐ MENTÉS TRY-CATCH BLOKKBAN
                try {
                    Player newPlayer = new Player(INITIAL_TOKENS);
                    // Ezeknek az UUID-knek UNIQUE-nak kell lenniük a DB-ben!
                    newPlayer.setIdempotencyKey(UUID.randomUUID());
                    newPlayer.setSessionId(currentHttpSessionId);

                    player = playerRepository.save(newPlayer);

                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                    // 2.2. HIBÁBÓL VALÓ FELÉPÜLÉS: A második Strict Mode kérés elkapja a hibát,
                    // mert a client_id/sessionId/idempotency_key már létezik (az 1. kérés elmentette).
                    // Ilyenkor visszakeressük a már létező játékost a session ID alapján.

                    player = playerRepository.findBySessionId(currentHttpSessionId);
                    if (player == null) {
                        // Ha a hiba megtörtént, de a Player nem található (nagyon ritka edge case),
                        // akkor valami komoly baj van.
                        throw new RuntimeException("Race Condition hiba! A Player nem található a Session ID alapján a hibakezelés után.", e);
                    }
                }

                // 2.3. Mentés a Redis Session-be (függetlenül attól, hogy mentettünk vagy visszakerestünk)
                session.setAttribute(PLAYER_ID_SESSION_KEY, player.getClientId());
            }
        }

        // DTO felépítése
        GameStateInit gameStateData = GameStateInit.builder()
            .deckLen(INITIAL_DECK_LENGTH)
            .build();

        return SessionInitResponse.builder()
            .status("success")
            .message("Játék session sikeresen indult.")
            .tokens(player.getTokens())
            .gameState(gameStateData)
            .gameStateHint("Place your bet.")
            .clientId(player.getClientId())
            .build();
    }
}
