package com.blackjack.blackjack.service;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class CookieServiceTest {
    @InjectMocks
    private CookieService cookieService;

    private final UUID TEST_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String CLIENT_ID_COOKIE_NAME = "blackjack_client_uuid";
    private static final int ONE_YEAR_IN_SECONDS = 365 * 24 * 60 * 60;

    // A BeforeEach metódus itt csak az InjectMocks inicializálását igényli, de benne hagyjuk
    // a konzisztencia érdekében, ha később függőségek kerülnének bele.
    @BeforeEach
    void setUp() {
        // MockitoExtension gondoskodik az inicializálásról
    }

    // ====================================================================================
    // 1. Teszt: createLongLivedClientIdCookie
    // ====================================================================================

    /**
     * Ellenőrzi, hogy a CookieService megfelelően hozza-e létre a klien azonosító Cookie-t,
     * beleértve a nevét, értékét és biztonsági attribútumait.
     */
    @Test
    void createLongLivedClientIdCookie_shouldCreateCookieWithCorrectAttributes() {
        Cookie cookie = cookieService.createLongLivedClientIdCookie(TEST_UUID);

        assertEquals(CLIENT_ID_COOKIE_NAME, cookie.getName(), "A cookie neve helytelen.");
        assertEquals(TEST_UUID.toString(), cookie.getValue(), "A cookie értéke helytelen.");

        assertEquals(ONE_YEAR_IN_SECONDS, cookie.getMaxAge(), "A cookie élettartama nem 1 év.");
        assertEquals("/", cookie.getPath(), "Az elérési út nem '/' a teljes alkalmazás elérhetőségéhez.");

        assertTrue(cookie.isHttpOnly(), "A cookie-nak HttpOnly-nak kell lennie.");
        assertTrue(cookie.getSecure(), "A cookie-nak Secure-nak kell lennie.");
    }

    // ====================================================================================
    // 2. Teszt: getClientIdFromCookies
    // ====================================================================================

    /**
     * Teszteli az esetet, amikor a keresett Cookie (blackjack_client_uuid) szerepel a tömbben.
     */
    @Test
    void getClientIdFromCookies_shouldReturnUuid_whenCookieIsPresent() {
        // A teszt UUID-vel beállított cookie-t hozzuk létre (valódi objektumként)
        Cookie expectedCookie = new Cookie(CLIENT_ID_COOKIE_NAME, TEST_UUID.toString());

        // A kérésben érkezett cookie tömb, sok mással együtt
        Cookie[] cookies = new Cookie[]{
            new Cookie("session_id", "abc"),
            expectedCookie,
            new Cookie("other_cookie", "123")
        };

        UUID result = cookieService.getClientIdFromCookies(cookies);

        assertNotNull(result, "UUID-t kellene visszaadnia.");
        assertEquals(TEST_UUID, result, "A kinyert UUID-nak meg kell egyeznie a beállítottal.");
    }

    /**
     * Teszteli, hogy null-t kapunk, ha a kérésben nem érkezett Cookie.
     */
    @Test
    void getClientIdFromCookies_shouldReturnNull_whenCookiesArrayIsNull() {
        UUID result = cookieService.getClientIdFromCookies(null);

        assertNull(result, "Null bemenet esetén null-t kell visszaadni.");
    }

    /**
     * Teszteli, hogy null-t kapunk, ha a keresett Cookie hiányzik a tömbből.
     */
    @Test
    void getClientIdFromCookies_shouldReturnNull_whenTargetCookieIsMissing() {
        // ARRANGE: Egy tömb, amely csak más, irreleváns cookie-kat tartalmaz
        Cookie[] cookies = new Cookie[]{
            new Cookie("session_id", "abc"),
            new Cookie("other_cookie", "123")
        };

        UUID result = cookieService.getClientIdFromCookies(cookies);

        assertNull(result, "Ha a keresett cookie hiányzik, null-t kell visszaadni.");
    }

    /**
     * Teszteli, hogy null-t kapunk, ha a Cookie-ban tárolt érték nem érvényes UUID.
     */
    @Test
    void getClientIdFromCookies_shouldReturnNull_whenCookieValueIsInvalidUuid() {
        // ARRANGE: Egy érvénytelen formátumú érték a Cookie-ban
        Cookie invalidCookie = new Cookie(CLIENT_ID_COOKIE_NAME, "not-a-valid-uuid-string");
        Cookie[] cookies = new Cookie[]{invalidCookie};

        UUID result = cookieService.getClientIdFromCookies(cookies);

        assertNull(result, "Érvénytelen UUID érték esetén null-t kell visszaadni.");
    }
}
