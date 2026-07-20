package com.blackjack.blackjack.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    public JwtFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        System.out.println("DEBUG Bejövő Authorization fejléc: " + authHeader);
        // 1. Ellenőrizzük, hogy van-e "Bearer " kezdetű fejléc
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                String clientId = jwtService.getClientIdFromToken(token);
                UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(clientId, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception e) {
                // Hiba esetén (pl. lejárt token) a kérés hitelesítés nélkül megy tovább
                logger.error("Érvénytelen JWT token: " + e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
