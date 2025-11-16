package com.blackjack.blackjack.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            // 1. KULCS: A CSRF védelem végleges kikapcsolása. (Megoldja a 403-as hibát a POST kéréseknél).
            .csrf(AbstractHttpConfigurer::disable)

            // 2. CORS engedélyezése.
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 3. Útvonal engedélyek beállítása
            .authorizeHttpRequests(authorize -> authorize
                // Engedélyezi a statikus fájlokat.
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico").permitAll()

                // Engedélyezi a CORS OPTIONS kéréseket (pre-flight).
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // JAVÍTÁS: Engedélyezi a belső hiba útvonalat. Ez kritikus, ha az /api/bet NullPointer-t dob!
                .requestMatchers("/error").permitAll()

                // Engedélyezi az összes API végponthoz való anonim hozzáférést.
                .requestMatchers("/api/**").permitAll()

                // Minden más kéréshez hitelesítés szükséges.
                .anyRequest().authenticated()
            )

            // 4. Nincs külön exceptionHandling beállítás, az alapértelmezett hibakezelés lép életbe.

            // 5. SESSION KEZELÉS: Spring Session (Redis) használata.
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )

            // 6. HTTP Basic Auth és FormLogin kikapcsolása.
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable);


        return http.build();
    }

    /**
     * CORS konfigurációs Bean a frontend (5173) engedélyezéséhez.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
