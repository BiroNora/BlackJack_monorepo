package com.blackjack.blackjack.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!test") // Teszt alatt NE fusson le
@Slf4j
public class RedisChecker {

    @Autowired(required = false) // Teszt alatt lehet null, ha mockoljuk
    private StringRedisTemplate redisTemplate;

    @PostConstruct
    public void testRedisConnection() {
        if (redisTemplate == null) return;

        log.info("--- Upstash Redis Kapcsolat Tesztelése ---");
        try {
            String pingResult = redisTemplate.getConnectionFactory().getConnection().ping();
            log.info("✅ REDIS KAPCSOLAT SIKERES: {}", pingResult);
        } catch (Exception e) {
            log.error("❌ REDIS HIBA: {}", e.getMessage());
        }
    }
}
