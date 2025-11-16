package com.blackjack.blackjack.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.CookieSerializer;

/**
 * Konfiguráció a JSESSIONID cookie SameSite és Secure beállításainak felülírására.
 *
 * Mivel a böngészők a Cross-Origin kérések (5173 -> 8080) során blokkolják a
 * HTTP-n keresztül beállított SameSite=Lax cookiet, ezt a konfigurációt
 * használjuk a fejlesztői környezetben a SameSite attribútum eltávolítására.
 *
 * FIGYELEM: Ezt a konfigurációt ÉLES, HTTPS-t használó környezetben NEM szabad
 * beállítani, ott a SameSite=None és Secure=true a helyes.
 */
@Configuration
public class SessionCookieConfig {

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();

        // 1. Kikapcsolja a SameSite attribútumot (az üres String beállítás helyett)
        // A null érték kényszeríti a Tomcatet, hogy ne adjon hozzá SameSite-ot a válaszhoz.
        serializer.setSameSite(null);

        // 2. Kikapcsolja a Secure attribútumot, mivel HTTP-t használunk.
        serializer.setUseSecureCookie(false);

        // A cookie nevét a session.cookie.cookie-name tulajdonságból veszi át (JSESSIONID)
        // serializer.setCookieName("JSESSIONID");

        return serializer;
    }
}
