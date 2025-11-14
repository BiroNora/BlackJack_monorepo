package com.blackjack.blackjack.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.http.HttpMethod;
import java.util.Arrays;
import org.springframework.security.config.http.SessionCreationPolicy;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            // 1. KÖTELEZŐ: CORS engedélyezése a Security Filter Chain-ben.
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 2. Útvonal engedélyek beállítása
            .authorizeHttpRequests(authorize -> authorize
                // 🔑 JAVÍTÁS A 403-RA: Engedélyezi a CORS OPTIONS kéréseket minden útvonalra (Pre-flight).
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Engedélyezi az initialize_session végponthoz való anonim hozzáférést.
                .requestMatchers("/api/initialize_session").permitAll()

                // Minden más kéréshez hitelesítés szükséges.
                .anyRequest().authenticated()
            )

            // 3. CSRF védelem kikapcsolása (CORS és Session ID használat miatt).
            .csrf(AbstractHttpConfigurer::disable)

            // 4. SESSION KEZELÉS: Megerősítjük, hogy a Spring Session-t (Redis) használja.
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )

            // 5. HTTP Basic Auth kikapcsolása.
            .httpBasic(AbstractHttpConfigurer::disable)

            // 6. Kikapcsolja a bejelentkezési formot, ami átirányítási hibát okozhat API-knál.
            .formLogin(AbstractHttpConfigurer::disable);


        return http.build();
    }

    /**
     * CORS konfigurációs Bean, ami a Spring Security Filterben fut le.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 🔑 HELYES PORT: PONTOSAN a VITE frontend URL-je (5173-as port).
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:5173"));

        // Minden metódust engedélyezünk, beleértve az OPTIONS-ot is.
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));

        // 🔑 KULCS: Ez engedélyezi a JSESSIONID cookie átvitelét a tartományok között.
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
