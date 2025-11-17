package com.blackjack.blackjack;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@SpringBootApplication
@EnableRedisHttpSession
public class BlackjackApplication {
    @Autowired
    private StringRedisTemplate redisTemplate;

    public static void main(String[] args) {
        SpringApplication.run(BlackjackApplication.class, args);
        System.out.println("--- Deck Tesztelés ---");
    }

    @PostConstruct
    public void testRedisConnection() {
        System.out.println("------------------------------------------");
        System.out.println("--- PRÓBA: Upstash Redis Kapcsolat Tesztelése ---");
        try {
            // A Redis Template használata, hogy elküldje a 'ping' parancsot
            String pingResult = redisTemplate.getConnectionFactory()
                .getConnection()
                .ping();

            // A ping parancs sikeresen lefut, ha a válasz 'PONG'
            if ("PONG".equals(pingResult)) {
                System.out.println("✅ REDIS KAPCSOLAT SIKERES! Válasz: PONG");

                // Még egy gyors teszt: SET és GET
                redisTemplate.opsForValue().set("redis-test-key", "UPSTASH-OK");
                String value = redisTemplate.opsForValue().get("redis-test-key");
                System.out.println("✅ Redis SET/GET sikeres. Lekért érték: " + value);
            } else {
                System.err.println("❌ REDIS KAPCSOLAT HIBA! Ping válasz: " + pingResult);
            }
        } catch (Exception e) {
            System.err.println("❌ KRITIKUS REDIS KAPCSOLATI HIBA! Nem sikerült csatlakozni:");
            e.printStackTrace();
            // Itt dobhatsz is hibát, de most csak logoljunk
        }
        System.out.println("------------------------------------------");
    }

}
