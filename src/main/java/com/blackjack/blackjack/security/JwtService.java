package com.blackjack.blackjack.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Getter
@Service
public class JwtService {

    private final SecretKey key = Keys.hmacShaKeyFor("a-hosszú-titkos-kulcsod-ami-legalább-32-karakter-hosszú-kell-hogy-legyen".getBytes());

    // Token generálása (új 0.12+ szintaktika)
    public String generateToken(String clientId) {
        return Jwts.builder()
            .subject(clientId)
            .id(UUID.randomUUID().toString())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24)) // 24 óra
            .signWith(key)
            .compact();
    }

    // Token validálása és adatok kinyerése
    public String getClientIdFromToken(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getSubject();
    }
}
