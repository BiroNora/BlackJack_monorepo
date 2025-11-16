package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.GameState;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service // Spring komponenssé tesszük
public class GameStateService {

    private static final String GAME_STATE_KEY_PREFIX = "game_state:";
    // Ajánlott: Beállítjuk a session lejáratát (pl. 30 perc inaktivitás után)
    private static final long SESSION_TIMEOUT_MINUTES = 30;

    // RedisTemplate injektálása (String kulcs és GameState érték)
    private final RedisTemplate<String, GameState> redisTemplate;

    public GameStateService(RedisTemplate<String, GameState> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String buildKey(UUID clientId) {
        // A kulcsot a GameState_prefix + ClientID UUID-ből generáljuk
        return GAME_STATE_KEY_PREFIX + clientId.toString();
    }

    /**
     * Visszaadja az aktuális GameState objektumot a Redisből.
     */
    public Optional<GameState> getGameState(UUID clientId) {
        String key = buildKey(clientId);

        // Lekérdezzük az értéket a kulcs alapján
        GameState gameState = redisTemplate.opsForValue().get(key);

        // Ha sikeres a lekérés, frissítjük a session időzítőjét (timeout)
        if (gameState != null) {
            redisTemplate.expire(key, SESSION_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        }

        return Optional.ofNullable(gameState);
    }

    /**
     * Ezt a metódust használja majd a Controller/GameService a GameState mentésére.
     */
    public void saveGameState(GameState gameState) {
        String key = buildKey(gameState.getClientId());

        // Elmenti az objektumot és beállítja a lejárati időt
        redisTemplate.opsForValue().set(key, gameState, SESSION_TIMEOUT_MINUTES, TimeUnit.MINUTES);
    }

    // ... további metódusok (pl. removeGameState)
}
