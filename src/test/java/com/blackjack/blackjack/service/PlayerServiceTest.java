package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PlayerServiceTest {
    @InjectMocks
    private PlayerService playerService;
    @Mock
    private PlayerRepository playerRepository;

    // Konstansok a Player entitás minden kötelező mezőjéhez
    private final UUID TEST_ID = UUID.randomUUID();
    private final UUID CLIENT_UUID = UUID.randomUUID();
    private final String SESSION_ID = "test-session";
    private final UUID IDEMPOTENCY_KEY = UUID.randomUUID();

    // A service-nek átadott Player objektum alapállapota
    private Player initialPlayer;

    @BeforeEach
    void setUp() {
        // Inicializálunk egy alapértelmezett, teljes Player objektumot minden teszt előtt.
        // Ezt a konkrét objektumot adjuk át a service metódusainak bemenetként.
        initialPlayer = Player.builder()
            .id(TEST_ID)
            .clientId(CLIENT_UUID)
            .sessionId(SESSION_ID)
            .tokens(200) // Alapértelmezett tokens érték
            .idempotencyKey(IDEMPOTENCY_KEY)
            .lastActivity(OffsetDateTime.now())
            .build();
    }

    @Test
    void deductBet_shouldDeductTokensAndSave() {
        final int betAmount = 50;
        final int expectedTokens = initialPlayer.getTokens() - betAmount;

        // Mock beállítása: A repository adja vissza azt az új Player objektumot, amit kapott
        when(playerRepository.save(any(Player.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Player result = playerService.deductBet(initialPlayer, betAmount);

        // Assert: Ellenőrizzük, hogy a save() metódus a helyes állapotú ÚJ Player objektummal hívódott-e
        verify(playerRepository, times(1)).save(argThat(savedPlayer ->
            // A tokens értéknek a vártnak (150) kell lennie
            savedPlayer.getTokens() == expectedTokens &&
                // Az azonosítóknak (ID, ClientID) meg kell egyezniük az eredetivel
                savedPlayer.getClientId().equals(CLIENT_UUID) &&
                savedPlayer.getId().equals(TEST_ID)
        ));

        // Ellenőrizzük a visszatérési értéket
        assertEquals(expectedTokens, result.getTokens());
        // Ellenőrizzük, hogy az eredeti objektum nem változott
        assertEquals(200, initialPlayer.getTokens());
    }

    @Test
    void deductBet_shouldThrowExceptionForInsufficientTokens() {
        final int initialTokens = 20;
        final int betAmount = 50;

        // Készítünk egy Player objektumot kevés tokennel
        Player lowTokenPlayer = initialPlayer.toBuilder().tokens(initialTokens).build();

        // Act & Assert
        IllegalArgumentException thrown = assertThrows(
            IllegalArgumentException.class,
            () -> playerService.deductBet(lowTokenPlayer, betAmount)
        );

        assertEquals("Insufficient tokens.", thrown.getMessage());

        // Ellenőrizzük, hogy a save() soha nem hívódott meg
        verify(playerRepository, never()).save(any());

        // Ellenőrizzük, hogy az eredeti objektum nem változott
        assertEquals(initialTokens, lowTokenPlayer.getTokens());
    }

    @Test
    void updateTokens_shouldIncreaseTokensAndSave() {
        final int initialTokens = 100;
        final int rewardAmount = 250;
        final int expectedTokens = 350;

        // Készítünk egy Player objektumot a teszthez szükséges initialTokens értékkel
        Player playerToUpdate = initialPlayer.toBuilder().tokens(initialTokens).build();

        // Mock beállítása
        when(playerRepository.save(any(Player.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Player result = playerService.updateTokens(playerToUpdate, rewardAmount);

        // Assert: Ellenőrizzük, hogy a save() metódus a helyes állapotú ÚJ Player objektummal hívódott-e
        verify(playerRepository, times(1)).save(argThat(savedPlayer ->
            savedPlayer.getTokens() == expectedTokens
        ));

        // Ellenőrizzük a visszatérési értéket
        assertEquals(expectedTokens, result.getTokens());
    }

    @Test
    void resetTokens_shouldSetTokensToInitialValueAndSave() {
        final int resetValue = 1000;
        final int initialTokens = 500;

        // Készítünk egy Player objektumot a teszthez szükséges initialTokens értékkel
        Player playerToReset = initialPlayer.toBuilder().tokens(initialTokens).build();

        // Mock beállítása
        when(playerRepository.save(any(Player.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Player result = playerService.resetTokens(playerToReset, resetValue);

        // Assert: Ellenőrizzük, hogy a save() metódus a helyes állapotú ÚJ Player objektummal hívódott-e
        verify(playerRepository, times(1)).save(argThat(savedPlayer ->
            savedPlayer.getTokens() == resetValue
        ));

        // Ellenőrizzük a visszatérési értéket
        assertEquals(resetValue, result.getTokens());
    }
}
