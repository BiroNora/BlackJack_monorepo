package com.blackjack.blackjack.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.config.ConfigureRedisAction;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@Configuration
@EnableRedisHttpSession
public class RedisConfig {

    // Ezt a bean-t most már NEM A SESSION-HÖZ, hanem a custom GameStateService-hez használjuk
    @Bean
    public <T> RedisTemplate<String, T> generalRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, T> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());

        // A T típus szerializálásához továbbra is a Jackson-t használjuk
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        return template;
    }

    // --- KRITIKUS JAVÍTÁS A SPRING SESSION-HÖZ ---

    /**
     * Ez a bean konfigurálja a Spring Session-t, hogy
     * A) a kulcsokat Stringként tárolja
     * B) az értékeket (beleértve a belső metaadatokat is, mint a maxInactiveInterval)
     * mindig GenericJackson2JsonRedisSerializer-rel tárolja.
     * Ezzel eltávolítjuk az inkompatibilis JdkSerializationRedisSerializer-t.
     */
    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        // Használja a Jackson-t minden objektum szerializálására, beleértve a session metaadatokat.
        return new GenericJackson2JsonRedisSerializer();
    }

    // Ez egy szükséges beállítás, ha az Upstash-t használja, ami nem engedi a CONFIG parancsot.
    @Bean
    public ConfigureRedisAction configureRedisAction() {
        return ConfigureRedisAction.NO_OP;
    }
}
