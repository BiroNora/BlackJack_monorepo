package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PlayerServiceTest {
    @InjectMocks
    private PlayerService playerService;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private Player mockPlayer;

    @Test
    void deductBet_shouldDeductTokensAndSave() {
        final int initialTokens = 200;
        final int betAmount = 50;
        final int expectedTokens = 150;

        when(mockPlayer.getTokens()).thenReturn(initialTokens);
        when(playerRepository.save(mockPlayer)).thenReturn(mockPlayer); // Simulate save

        Player result = playerService.deductBet(mockPlayer, betAmount);

        // Ellenőrizzük, hogy a setTokens() a helyes értékkel hívódott-e
        verify(mockPlayer, times(1)).setTokens(expectedTokens);
        // Ellenőrizzük, hogy a save() hívódott-e
        verify(playerRepository, times(1)).save(mockPlayer);
        // Ellenőrizzük, hogy a visszatérési érték a mentett játékos-e
        assertEquals(mockPlayer, result);
    }

    @Test
    void deductBet_shouldThrowExceptionForInsufficientTokens() {
        final int initialTokens = 20;
        final int betAmount = 50;

        when(mockPlayer.getTokens()).thenReturn(initialTokens);

        // Ellenőrizzük, hogy a kivétel dobása megtörténik-e
        IllegalArgumentException thrown = assertThrows(
            IllegalArgumentException.class,
            () -> playerService.deductBet(mockPlayer, betAmount)
        );

        assertEquals("Insufficient tokens.", thrown.getMessage());

        // Ellenőrizzük, hogy a setTokens() és a save() soha nem hívódott meg
        verify(mockPlayer, never()).setTokens(anyInt());
        verify(playerRepository, never()).save(any());
    }

    @Test
    void updateTokens_shouldIncreaseTokensAndSave() {
        final int initialTokens = 100;
        final int rewardAmount = 250;
        final int expectedTokens = 350;

        when(mockPlayer.getTokens()).thenReturn(initialTokens);
        when(playerRepository.save(mockPlayer)).thenReturn(mockPlayer);

        Player result = playerService.updateTokens(mockPlayer, rewardAmount);

        // Ellenőrizzük, hogy a setTokens() a helyes, növelt értékkel lett-e meghívva
        verify(mockPlayer, times(1)).setTokens(expectedTokens);
        // Ellenőrizzük, hogy a save() hívódott-e
        verify(playerRepository, times(1)).save(mockPlayer);
        // Ellenőrizzük a visszatérési értéket
        assertEquals(mockPlayer, result);
    }

    @Test
    void resetTokens_shouldSetTokensToInitialValueAndSave() {
        final int initialTokens = 500;

        when(playerRepository.save(mockPlayer)).thenReturn(mockPlayer);

        Player result = playerService.resetTokens(mockPlayer, initialTokens);

        // Ellenőrizzük, hogy a setTokens() az initialTokens értékkel lett-e meghívva
        verify(mockPlayer, times(1)).setTokens(initialTokens);
        // Ellenőrizzük, hogy a save() hívódott-e
        verify(playerRepository, times(1)).save(mockPlayer);
        // Ellenőrizzük a visszatérési értéket
        assertEquals(mockPlayer, result);
    }
}
