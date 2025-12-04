package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.GameState;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

/**
 * Szolgáltatás a GameState (Játékállapot) kezelésére az HttpSession (Redis) segítségével.
 * NEM HASZNÁL SAJÁT REDIS KULCSOT, hanem a session attribútumait.
 */
@Service
@Slf4j
public class GameStateService {
    private static final String GAME_STATE_ATTR_NAME = "gameState"; // A session attribútum neve

    public GameStateService() {
    }

    // Segítő metódus az aktuális HttpSession objektum eléréséhez
    private HttpSession getCurrentSession() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attr != null) {
            // Fontos: false paramétert használunk, hogy ne hozzon létre új sessiont, ha nincs.
            return attr.getRequest().getSession(false);
        }
        return null;
    }

    /**
     * Visszaadja az aktuális GameState objektumot a Sessionből.
     */
    public Optional<GameState> getGameState(UUID clientId) {
        // A clientId-t továbbra is beolvashatjuk, de a lekérdezés a Session-en keresztül történik.
        HttpSession session = getCurrentSession();

        if (session != null) {
            // Kiolvassa az objektumot a Session attribútumai közül
            GameState gameState = (GameState) session.getAttribute(GAME_STATE_ATTR_NAME);
            return Optional.ofNullable(gameState);
        }

        return Optional.empty(); // Nincs aktív session
    }

    /**
     * Menti a frissített GameState-et a Sessionbe (és ezzel automatikusan a Redisbe).
     */
    public void saveGameState(GameState gameState) {
        HttpSession session = getCurrentSession();

        if (session != null) {
            // Visszamentjük a MÓDOSÍTOTT GameState objektumot a Sessionbe.
            // A Spring Session/Redis erről gondoskodik.
            session.setAttribute(GAME_STATE_ATTR_NAME, gameState);
        } else {
            // Hibakezelés, ha a mentéskor valamiért eltűnt a session
            log.error("INTERNAL_UPDATE_FAILED");
        }
    }
}
