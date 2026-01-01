package com.blackjack.blackjack.controller;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@TestConfiguration
public class TestSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // Fejlesztés/Teszt alatt a CSRF-et tiltjuk
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll() // Minden kérést engedélyezünk a tesztben
            );
        return http.build();
    }
}
