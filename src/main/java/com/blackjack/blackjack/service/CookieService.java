package com.blackjack.blackjack.service;

import jakarta.servlet.http.Cookie;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CookieService {

    public static final String CLIENT_ID_COOKIE_NAME = "blackjack_client_uuid";
    private static final int ONE_YEAR_IN_SECONDS = 365 * 24 * 60 * 60;

    /**
     * Létrehoz egy hosszú élettartamú Cookie-t a Player azonosító (UUID) tárolására.
     * Ez a Cookie felel a session-ök közötti perzisztenciáért ("holnapi" visszatérés).
     * @param clientId Az érvényes UUID, amit a Cookie-ban tárolni kell.
     * @return A beállított Cookie objektum.
     */
    public Cookie createLongLivedClientIdCookie(UUID clientId) {
        Cookie cookie = new Cookie(CLIENT_ID_COOKIE_NAME, clientId.toString());
        // A cookie élettartama 1 év, ami garantálja a session-ök közötti perzisztenciát
        cookie.setMaxAge(ONE_YEAR_IN_SECONDS);
        cookie.setPath("/"); // Elérhetővé tesszük a teljes alkalmazás számára
        cookie.setHttpOnly(true); // Biztonsági beállítás: JavaScript nem fér hozzá
        cookie.setSecure(true); // Csak HTTPS-en keresztül küldjük el (éles környezetben)
        return cookie;
    }

    /**
     * Keresi és visszaadja a Player ID-t a bejövő Cookie-k közül.
     * @param cookies A kérésben érkezett Cookie tömb.
     * @return A client_id UUID-ként, vagy null, ha a Cookie hiányzik vagy hibás.
     */
    public UUID getClientIdFromCookies(Cookie[] cookies) {
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (CLIENT_ID_COOKIE_NAME.equals(cookie.getName())) {
                try {
                    // Megpróbáljuk visszaadni a mentett ID-t
                    return UUID.fromString(cookie.getValue());
                } catch (IllegalArgumentException e) {
                    // Ha a mentett érték nem érvényes UUID, figyelmen kívül hagyjuk
                    return null;
                }
            }
        }
        return null;
    }
}
