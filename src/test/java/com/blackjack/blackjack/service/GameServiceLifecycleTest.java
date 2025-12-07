package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GameServiceLifecycleTest {
    @Spy
    @InjectMocks
    private GameService gameService;

    @Mock
    private GameStateService gameStateService;
    @Mock
    private GameRuleEngine gameRuleEngine;
    @Mock
    private GameStateManager gameStateManager;
    @Mock
    private DeckService deckService;

    private GameState mockGameState;
    private final Optional<GameState> optionalMockGameState = Optional.of(mock(GameState.class));
    private final Card testCardTen = new Card(Suit.HEARTS, Rank.TEN);
    private final Card testCardAce = new Card(Suit.HEARTS, Rank.ACE);

    @BeforeEach
    void setUp() {
        mockGameState = mock(GameState.class);

        when(gameStateManager.getGameStateOrThrow(any(Optional.class))).thenReturn(mockGameState);
    }

    @Test
    void testPlaceBetShouldUpdateBetAndSaveState() {
        final int initialBet = 50;
        final int betAmount = 25;

        when(mockGameState.getBet()).thenReturn(initialBet);
        when(mockGameState.getBetList()).thenReturn(new ArrayList<>(List.of(initialBet)));

        GameState result = gameService.placeBet(optionalMockGameState, betAmount);

        assertEquals(mockGameState, result);

        verify(mockGameState).setBetList(argThat(list -> list.contains(initialBet) && list.contains(betAmount)));
        verify(mockGameState).setBet(initialBet + betAmount);
        verify(gameStateService).saveGameState(mockGameState);
    }

    @Test
    void testPlaceBetShouldInitializeNewBetListWhenNull() {
        final int betAmount = 25;
        when(mockGameState.getBetList()).thenReturn(null);
        when(mockGameState.getBet()).thenReturn(0);

        gameService.placeBet(optionalMockGameState, betAmount);

        verify(mockGameState).setBetList(argThat(list -> list != null && list.contains(betAmount)));
        verify(mockGameState).setBet(betAmount);
        verify(gameStateService).saveGameState(mockGameState);
    }

    @Test
    void testRetakeBetShouldReturnLastBetAndSaveState() {
        final int initialTotalBet = 100;
        final int lastBet = 25;
        List<Integer> betList = new ArrayList<>(List.of(50, 25, lastBet)); // [50, 25, 25]

        when(mockGameState.getBet()).thenReturn(initialTotalBet);
        when(mockGameState.getBetList()).thenReturn(betList);

        int amountReturned = gameService.retakeBet(optionalMockGameState);

        assertEquals(-lastBet, amountReturned);

        verify(mockGameState).setBet(initialTotalBet - lastBet);
        verify(gameStateService).saveGameState(mockGameState);
        assertEquals(2, betList.size());
    }

    @Test
    void testRetakeBetShouldThrowExceptionWhenBetListIsEmpty() {
        when(mockGameState.getBetList()).thenReturn(new ArrayList<>());

        assertThrows(ResponseStatusException.class, () -> gameService.retakeBet(optionalMockGameState));
    }

    @Test
    void testCreateNewDeckShouldCallDeckServiceAndSaveState() {
        Deck newMockDeck = mock(Deck.class);

        when(deckService.createNewDeck()).thenReturn(newMockDeck);

        gameService.createNewDeck(optionalMockGameState);

        verify(deckService).createNewDeck();
        verify(mockGameState).setDeck(newMockDeck);
        verify(gameStateService).saveGameState(mockGameState);
    }

    @Test
    void testSafeDealCardShouldReturnCardAndSaveState() {
        Deck mockDeck = mock(Deck.class);

        when(mockGameState.getDeck()).thenReturn(mockDeck);
        when(mockDeck.getDeckLength()).thenReturn(50);
        when(deckService.dealCard(mockDeck)).thenReturn(testCardTen);

        Card card = gameService.safeDealCard(optionalMockGameState);

        assertEquals(testCardTen, card);
        verify(deckService).dealCard(mockDeck);
        verify(gameStateService).saveGameState(mockGameState);
        verify(gameStateManager, never()).getGameStateOrThrow(Optional.empty());
    }

    @Test
    void testSafeDealCardShouldCreateNewDeckWhenDeckIsNull() {
        when(mockGameState.getDeck()).thenReturn(null);

        doReturn(mockGameState).when(this.gameService).createNewDeck(any());

        Deck newMockDeck = mock(Deck.class);
        when(mockGameState.getDeck()).thenReturn(newMockDeck);
        when(deckService.dealCard(newMockDeck)).thenReturn(testCardAce);

        Card card = gameService.safeDealCard(optionalMockGameState);

        assertEquals(testCardAce, card);
        verify(this.gameService).createNewDeck(any());
        verify(deckService).dealCard(newMockDeck);
    }

    @Test
    void testPlayerHitShouldDealCardAndAddToPlayerHand() {
        when(mockGameState.isRoundActive()).thenReturn(true);

        doReturn(testCardAce).when(this.gameService).safeDealCard(any());

        PlayerHand mockPlayerHand = mock(PlayerHand.class);
        LinkedList<Card> mockHandList = new LinkedList<>();
        when(mockGameState.getPlayer()).thenReturn(mockPlayerHand);
        when(mockPlayerHand.getHand()).thenReturn(mockHandList);

        gameService.playerHit(optionalMockGameState);

        verify(this.gameService).safeDealCard(any());
        assertTrue(mockHandList.contains(testCardAce));
        verify(gameStateService).saveGameState(mockGameState);
    }

    @Test
    void testPlayerStandShouldForceDealerToHitUntilSeventeen() {
        try (MockedStatic<HandValueCalculator> mockedCalc = mockStatic(HandValueCalculator.class)) {
            // PlayerHand mockolása a NullPointerException elkerülése érdekében
            PlayerHand mockPlayerHand = mock(PlayerHand.class);
            when(mockGameState.getPlayer()).thenReturn(mockPlayerHand);
            // PlayerHand sum beállítása, hogy a winnerStateUpdater lefutthasson
            when(mockPlayerHand.getSum()).thenReturn(18);

            DealerHandUnmasked mockDealerHand = mock(DealerHandUnmasked.class);
            when(mockGameState.getDealerUnmasked()).thenReturn(mockDealerHand);

            LinkedList<Card> dealerHandList = new LinkedList<>();
            when(mockDealerHand.getHand()).thenReturn(dealerHandList);

            // 1. Dealer kezdeti összege (alacsony, 16)
            // 2. Dealer második összege (még alacsony, 16)
            // 3. Dealer harmadik összege (elég, 17)
            when(mockDealerHand.getSum()).thenReturn(16, 16, 17);

            doReturn(testCardTen).doReturn(testCardAce).when(gameService).safeDealCard(any());

            gameService.playerStand(optionalMockGameState);

            // Dealer húzott-e lapot legalább 2-szer (16 -> 16 -> 17)
            verify(gameService, times(2)).safeDealCard(any());
            // A dealer kéz megkapta-e a 2 lapot
            assertEquals(2, dealerHandList.size());
            verify(gameRuleEngine).winnerStateUpdater(mockGameState);
            verify(gameStateService).saveGameState(mockGameState);
        }
    }

    @Test
    void testInitializeNewRoundShouldDealFourCardsAndSetInitialStates() {
        GameState resetGameState = mock(GameState.class);
        PlayerHand mockPlayerHand = mock(PlayerHand.class);
        DealerHandUnmasked mockDealerUnmasked = mock(DealerHandUnmasked.class);
        DealerHandMasked mockDealerMasked = mock(DealerHandMasked.class);
        LinkedList<Card> playerHandList = new LinkedList<>();
        LinkedList<Card> dealerUnmaskedHandList = new LinkedList<>();

        when(gameStateManager.resetRoundState(mockGameState)).thenReturn(resetGameState);
        when(resetGameState.getPlayer()).thenReturn(mockPlayerHand);
        when(mockPlayerHand.getHand()).thenReturn(playerHandList);
        when(resetGameState.getDealerUnmasked()).thenReturn(mockDealerUnmasked);
        when(mockDealerUnmasked.getHand()).thenReturn(dealerUnmaskedHandList);
        when(resetGameState.getDealerMasked()).thenReturn(mockDealerMasked);

        doReturn(testCardTen).doReturn(testCardAce).doReturn(testCardTen).doReturn(testCardTen)
            .when(this.gameService).safeDealCard(any());

        when(gameRuleEngine.canSplit(any())).thenReturn(false);
        when(gameRuleEngine.initNatural21State(any())).thenReturn(WinnerState.NONE.getValue()); // Nincs Natural BJ

        try (MockedStatic<HandValueCalculator> mockedCalc = mockStatic(HandValueCalculator.class)) {
            when(HandValueCalculator.calculateSum(any())).thenReturn(10);

            gameService.initializeNewRound(optionalMockGameState);

            verify(this.gameService, times(4)).safeDealCard(any());
            assertEquals(2, playerHandList.size());
            verify(resetGameState).setRoundActive(true);
            verify(mockPlayerHand).setCanSplit(false);
            verify(mockDealerMasked).setSum(10);
            verify(gameStateService).saveGameState(resetGameState);
        }
    }
}
