package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.DealerHandUnmasked;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.PlayerHand;
import com.blackjack.blackjack.model.WinnerState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GameServiceLogicTest {
    @Mock
    private GameState mockGameState;
    @Mock
    private PlayerHand mockPlayerHand;
    @Mock
    private DealerHandUnmasked mockDealerUnmasked;
    @Mock
    private GameStateService gameStateService;
    @InjectMocks
    private GameService gameService;

    @Test
    void calculateRewards_shouldAwardDoubleForPlayerWin() {
        final int initialBet = 50;
        final int expectedReward = 100; // 50 * 2.0

        when(mockPlayerHand.getBet()).thenReturn(initialBet);
        when(mockGameState.getPlayer()).thenReturn(mockPlayerHand);
        when(mockGameState.getDealerUnmasked()).thenReturn(mockDealerUnmasked);

        // Kulcsfontosságú beállítás: Játékos nyert, de nem Blackjackkel
        when(mockGameState.getWinner()).thenReturn(WinnerState.PLAYER_WON.getValue());
        when(mockGameState.getNatural21()).thenReturn(WinnerState.NONE.getValue());
        when(mockDealerUnmasked.getNatural21()).thenReturn(WinnerState.NONE.getValue()); // Dealer sem Blackjack

        // ACT
        GameService.TransactionResult result = gameService.calculateRewards(mockGameState);

        // ASSERT
        assertEquals(expectedReward, result.transactionResult());
        verify(mockPlayerHand).setBet(0);
        verify(mockGameState).setBet(0);
        verify(mockGameState).setRoundActive(false);
        verify(gameStateService).saveGameState(mockGameState);
    }

    @Test
    void calculateRewards_shouldAward2_5xForPlayerBlackjack() {
        final int initialBet = 40; // 40 * 2.5 = 100
        final int expectedReward = 100;

        when(mockPlayerHand.getBet()).thenReturn(initialBet);
        when(mockGameState.getPlayer()).thenReturn(mockPlayerHand);
        when(mockGameState.getDealerUnmasked()).thenReturn(mockDealerUnmasked);

        // Kulcsfontosságú beállítás: Játékos Blackjack-el nyert
        when(mockGameState.getNatural21()).thenReturn(WinnerState.BLACKJACK_PLAYER_WON.getValue());
        when(mockGameState.getWinner()).thenReturn(WinnerState.BLACKJACK_PLAYER_WON.getValue());
        when(mockDealerUnmasked.getNatural21()).thenReturn(WinnerState.NONE.getValue());

        // ACT
        GameService.TransactionResult result = gameService.calculateRewards(mockGameState);

        // ASSERT
        assertEquals(expectedReward, result.transactionResult());
        verify(mockPlayerHand).setBet(0);
        verify(mockGameState).setBet(0);
        verify(mockGameState).setRoundActive(false);
        verify(gameStateService).saveGameState(mockGameState);
    }

    @Test
    void calculateRewards_shouldRefundBetForPush() {
        final int initialBet = 75;
        final int expectedReward = 75; // 75 * 1.0

        when(mockPlayerHand.getBet()).thenReturn(initialBet);
        when(mockGameState.getPlayer()).thenReturn(mockPlayerHand);
        when(mockGameState.getDealerUnmasked()).thenReturn(mockDealerUnmasked);

        // Kulcsfontosságú beállítás: Általános PUSH (döntetlen)
        when(mockGameState.getWinner()).thenReturn(WinnerState.PUSH.getValue());
        when(mockGameState.getNatural21()).thenReturn(WinnerState.NONE.getValue());
        when(mockDealerUnmasked.getNatural21()).thenReturn(WinnerState.NONE.getValue());

        // ACT
        GameService.TransactionResult result = gameService.calculateRewards(mockGameState);

        // ASSERT
        assertEquals(expectedReward, result.transactionResult());
        verify(mockPlayerHand).setBet(0);
        verify(mockGameState).setBet(0);
        verify(mockGameState).setRoundActive(false);
        verify(gameStateService).saveGameState(mockGameState);
    }

    @Test
    void calculateRewards_shouldAwardZeroForPlayerLoss() {
        final int initialBet = 100;
        final int expectedReward = 0; // 100 * 0.0

        when(mockPlayerHand.getBet()).thenReturn(initialBet);
        when(mockGameState.getPlayer()).thenReturn(mockPlayerHand);
        when(mockGameState.getDealerUnmasked()).thenReturn(mockDealerUnmasked);

        // Kulcsfontosságú beállítás: Dealer nyert
        when(mockGameState.getWinner()).thenReturn(WinnerState.DEALER_WON.getValue()); // Vagy BUST, vagy DEALER_WON
        when(mockGameState.getNatural21()).thenReturn(WinnerState.NONE.getValue());
        when(mockDealerUnmasked.getNatural21()).thenReturn(WinnerState.NONE.getValue());

        // ACT
        GameService.TransactionResult result = gameService.calculateRewards(mockGameState);

        // ASSERT
        assertEquals(expectedReward, result.transactionResult());
        verify(mockPlayerHand).setBet(0);
        verify(mockGameState).setBet(0);
        verify(mockGameState).setRoundActive(false);
        verify(gameStateService).saveGameState(mockGameState);
    }

    @Test
    void calculateRewards_shouldAwardZeroIfDealerHadBlackjack() {
        final int initialBet = 100;
        final int expectedReward = 0;

        when(mockPlayerHand.getBet()).thenReturn(initialBet);
        when(mockGameState.getPlayer()).thenReturn(mockPlayerHand);
        when(mockGameState.getDealerUnmasked()).thenReturn(mockDealerUnmasked);

        // Kulcsfontosságú beállítás: Dealer Blackjack Nyert
        when(mockGameState.getWinner()).thenReturn(WinnerState.DEALER_WON.getValue()); // Bármi lehet, de a Dealer BJ a döntő
        when(mockGameState.getNatural21()).thenReturn(WinnerState.NONE.getValue());
        when(mockDealerUnmasked.getNatural21()).thenReturn(WinnerState.BLACKJACK_DEALER_WON.getValue());

        // ACT
        GameService.TransactionResult result = gameService.calculateRewards(mockGameState);

        // ASSERT
        assertEquals(expectedReward, result.transactionResult());
        verify(mockPlayerHand).setBet(0);
        verify(mockGameState).setBet(0);
        verify(mockGameState).setRoundActive(false);
        verify(gameStateService).saveGameState(mockGameState);
    }

    @Test
    void doubleRequest_shouldDoubleBetAndSaveState() {
        final int initialBet = 75;
        final int doubledBet = 150; // 75 * 2

        when(mockGameState.getPlayer()).thenReturn(mockPlayerHand);

        GameState result = gameService.doubleRequest(mockGameState, initialBet);

        // Ellenőrizzük, hogy a setBet metódus a helyes, megduplázott értékkel lett-e meghívva.
        verify(mockPlayerHand, times(1)).setBet(doubledBet);

        // Ellenőrizzük, hogy a GameStateService elmentette a módosított állapotot.
        verify(gameStateService, times(1)).saveGameState(mockGameState);

        // A metódusnak a bemeneti GameState-tel kell visszatérnie.
        assertEquals(mockGameState, result);
    }
}
