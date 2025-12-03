package com.blackjack.blackjack;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@SpringBootApplication
@EnableRedisHttpSession
@Slf4j
public class BlackjackApplication {
    @Autowired
    private StringRedisTemplate redisTemplate;

    public static void main(String[] args) {
        SpringApplication.run(BlackjackApplication.class, args);
        log.info("--- Deck Tesztelés ---");
    }

    @PostConstruct
    public void testRedisConnection() {
        log.info("------------------------------------------");
        log.info("--- PRÓBA: Upstash Redis Kapcsolat Tesztelése ---");
        try {
            // A Redis Template használata, hogy elküldje a 'ping' parancsot
            String pingResult = redisTemplate.getConnectionFactory()
                .getConnection()
                .ping();

            // A ping parancs sikeresen lefut, ha a válasz 'PONG'
            if ("PONG".equals(pingResult)) {
                log.info("✅ REDIS KAPCSOLAT SIKERES! Válasz: PONG");

                // Még egy gyors teszt: SET és GET
                redisTemplate.opsForValue().set("redis-test-key", "UPSTASH-OK");
                String value = redisTemplate.opsForValue().get("redis-test-key");
                log.info("✅ Redis SET/GET sikeres. Lekért érték: {}", value);
            } else {
                log.info("❌ REDIS KAPCSOLAT HIBA! Ping válasz: {}", pingResult);
            }
        } catch (Exception e) {
            log.info("❌ KRITIKUS REDIS KAPCSOLATI HIBA! Nem sikerült csatlakozni:");
            e.printStackTrace();
            // Itt dobhatsz is hibát, de most csak logoljunk
        }
        log.info("------------------------------------------");
    }

}
