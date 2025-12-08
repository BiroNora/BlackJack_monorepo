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

import java.util.*;

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
    void testPlayerHitShouldDoNothingWhenRoundIsNotActive() {
        when(mockGameState.isRoundActive()).thenReturn(false);

        GameState result = gameService.playerHit(optionalMockGameState);

        assertEquals(mockGameState, result);
        verify(this.gameService, never()).safeDealCard(any());
        verify(gameStateService, never()).saveGameState(any());
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

    @Test
    void testInsuranceRequest_DealerHasBlackjack() {
        final int currentBet = 100;
        PlayerHand mockPlayerHand = mock(PlayerHand.class);
        DealerHandUnmasked mockDealerUnmasked = mock(DealerHandUnmasked.class);

        when(mockGameState.getBet()).thenReturn(currentBet);
        when(mockGameState.getDealerUnmasked()).thenReturn(mockDealerUnmasked);
        when(mockDealerUnmasked.getNatural21()).thenReturn(WinnerState.BLACKJACK_DEALER_WON.getValue());
        when(mockGameState.getPlayer()).thenReturn(mockPlayerHand);

        GameService.TransactionResult result = gameService.insuranceRequest(optionalMockGameState);

        // A jutalom a teljes tét, 1:1.
        assertEquals(currentBet, result.transactionResult());

        verify(mockGameState).setBet(0);
        verify(mockGameState.getPlayer()).setBet(0);
        verify(mockGameState).setRoundActive(false);
        verify(mockGameState).setWinner(WinnerState.DEALER_WON.getValue());
        verify(gameStateService).saveGameState(mockGameState);
    }

    @Test
    void testInsuranceRequest_DealerNoBlackjack() {
        final int currentBet = 100;
        DealerHandUnmasked mockDealerUnmasked = mock(DealerHandUnmasked.class);

        when(mockGameState.getBet()).thenReturn(currentBet);
        when(mockGameState.getDealerUnmasked()).thenReturn(mockDealerUnmasked);
        when(mockDealerUnmasked.getNatural21()).thenReturn(WinnerState.NONE.getValue());

        GameService.TransactionResult result = gameService.insuranceRequest(optionalMockGameState);

        assertEquals(-50, result.transactionResult());

        verify(gameStateService).saveGameState(mockGameState);
        verify(mockGameState, never()).setRoundActive(false);
    }

    @Test
    void testSplitHandShouldDealSplitCardToBothHands() {
        PlayerHand mockPlayerHand = mock(PlayerHand.class);
        Map mockPlayersMap = mock(Map.class);
        LinkedList<Card> mockHandList = new LinkedList<>(List.of(testCardAce, testCardAce));

        when(mockGameState.getPlayer()).thenReturn(mockPlayerHand);
        when(mockGameState.getPlayers()).thenReturn(mockPlayersMap);
        when(mockPlayerHand.isCanSplit()).thenReturn(true);
        when(mockPlayerHand.getHand()).thenReturn(mockHandList);

        PlayerHand updatedHand = mock(PlayerHand.class);
        PlayerHand secondHand = mock(PlayerHand.class);
        when(secondHand.getId()).thenReturn("split-1");

        when(gameStateManager.dealSplitCard(eq(mockGameState), eq(true), isNull()))
            .thenReturn(updatedHand);
        when(gameStateManager.dealSplitCard(eq(mockGameState), eq(false), any(Card.class)))
            .thenReturn(secondHand);

        gameService.splitHand(optionalMockGameState);

        assertEquals(1, mockHandList.size());

        verify(mockGameState).setPlayer(updatedHand);
        verify(mockPlayersMap).put(eq("split-1"), eq(secondHand));
        verify(gameStateManager).sortPlayersMap(mockPlayersMap);
        verify(mockGameState).addSplitReq(1);
        verify(gameStateService).saveGameState(mockGameState);
    }

    @Test
    void testAddToPlayersListByStand_ShouldMovePlayerHandAndSave() {
        PlayerHand mockPlayerHand = mock(PlayerHand.class);
        Map mockPlayersMap = mock(Map.class);
        Map mockPlayersIndex = mock(Map.class);

        when(mockGameState.getPlayer()).thenReturn(mockPlayerHand);
        when(mockGameState.getPlayers()).thenReturn(mockPlayersMap);
        when(mockGameState.getPlayersIndex()).thenReturn(mockPlayersIndex);

        when(gameStateManager.areStatedFalseHands(mockPlayersMap)).thenReturn(true);
        when(mockPlayerHand.getId()).thenReturn("player-main");

        gameService.addToPlayersListByStand(optionalMockGameState);

        verify(mockPlayerHand).setStated(true);
        verify(mockPlayersMap).put("player-main", mockPlayerHand);
        verify(mockPlayersIndex).put("player-main", true);
        verify(gameStateService).saveGameState(mockGameState);
    }

    @Test
    void testAddPlayerFromPlayers_ShouldLoadFirstHandAndSave() {
        PlayerHand firstHand = mock(PlayerHand.class);
        PlayerHand secondHand = mock(PlayerHand.class);

        // Használjunk MÓDOSÍTHATÓ LinkedHashMap-et => .remove() hívás ne adjon UnsupportedOperationException-t,
        // és ne zavarja meg a Mockito referenciális nyomon követését.
        Map<String, PlayerHand> mockPlayersMap = new LinkedHashMap<>();
        mockPlayersMap.put("player-1", firstHand);
        mockPlayersMap.put("player-2", secondHand);

        when(mockGameState.getPlayers()).thenReturn(mockPlayersMap);

        gameService.addPlayerFromPlayers(optionalMockGameState);

        verify(mockGameState).setPlayer(eq(firstHand));
        verify(gameStateService).saveGameState(mockGameState);

        // Ellenőrzi, hogy a .remove() valóban eltávolította az elemet
        assertEquals(1, mockPlayersMap.size());
    }

    @Test
    void addSplitPlayerToGame_case1_switchToSplitHandWhenIdsMatch() {
        final String nextHandId = "H-002";
        LinkedList<Card> handWithTwoCards = new LinkedList<>(List.of(testCardTen, testCardAce));

        PlayerHand mockSplitPlayer = mock(PlayerHand.class);
        when(mockSplitPlayer.getHand()).thenReturn(handWithTwoCards); // MÉRET: 2
        when(mockSplitPlayer.getId()).thenReturn(nextHandId);

        PlayerHand mockCurrentPlayer = mock(PlayerHand.class);

        Map<String, Boolean> playersIndex = Map.of(nextHandId, false, "P-001", true);
        when(mockGameState.getPlayersIndex()).thenReturn(playersIndex);
        when(gameStateManager.findNextActivePlayerId(playersIndex)).thenReturn(Optional.of(nextHandId));

        // ESET 1 feltételek:
        when(mockGameState.getSplitPlayer()).thenReturn(mockSplitPlayer);

        // *** SOROS MOCKOLÁS BEÁLLÍTÁSA A STÁTUSZVÁLTOZÁS SZIMULÁLÁSÁHOZ ***
        // 1. A metódus elején a getPlayer() a mockCurrentPlayer-t adja vissza.
        // 2. A metódus végén (a setPlayer hívása után) a getPlayer() a mockSplitPlayer-t adja vissza.
        when(mockGameState.getPlayer())
            .thenReturn(mockCurrentPlayer)
            .thenReturn(mockSplitPlayer);


        gameService.addSplitPlayerToGame(Optional.of(mockGameState));

        // A) Verifikáljuk a setPlayer hívást
        verify(mockGameState, times(1)).setPlayer(mockSplitPlayer);

        // B) Ellenőrizni, hogy a Card Deal logika KI LETT HAGYVA.
        verify(gameService, never()).safeDealCard(any());
        verify(gameRuleEngine, never()).canSplit(any());

        // C) Ellenőrizni a mentést
        verify(gameStateService, times(1)).saveGameState(mockGameState);

        // D) ESET 3 metódusainak ellenőrzése
        verify(mockGameState, never()).addSplitReq(anyInt());
        verify(gameStateManager, never()).deepCopyPlayerHand(any());
    }

    @Test
    void addSplitPlayerToGame_case2_fastExitWhenCurrentPlayerIsNext() {
        final String currentPlayerId = "P-001";
        PlayerHand mockPlayer = mock(PlayerHand.class);
        LinkedList<Card> handWithTwoCards = new LinkedList<>(List.of(testCardTen, testCardAce));

        when(mockPlayer.getHand()).thenReturn(handWithTwoCards);
        when(mockPlayer.getId()).thenReturn(currentPlayerId);

        Map<String, Boolean> playersIndex = Map.of(currentPlayerId, false);
        when(mockGameState.getPlayersIndex()).thenReturn(playersIndex);

        when(gameStateManager.findNextActivePlayerId(playersIndex)).thenReturn(Optional.of(currentPlayerId));

        // 1.3 ESET 1 kihagyása
        when(mockGameState.getPlayer()).thenReturn(mockPlayer);   // player != null
        when(mockGameState.getSplitPlayer()).thenReturn(null);    // splitPlayer == null -> Kihagyja az ESET 1-et

        gameService.addSplitPlayerToGame(Optional.of(mockGameState));

        // A) Az ESET 2 (gyors kiút) nem hívja a setPlayer metódust, mivel a lap már be van töltve.
        verify(mockGameState, never()).setPlayer(any(PlayerHand.class));

        // B) Ellenőrizni, hogy a Card Deal logika KI LETT HAGYVA.
        // Mivel a handWithTwoCards.size() = 2, a feltétel (size < 2) hamis.
        verify(gameService, never()).safeDealCard(any());
        verify(gameRuleEngine, never()).canSplit(any());

        // C) Ellenőrizni a mentést (ami a metódus végén van)
        verify(gameStateService, times(1)).saveGameState(mockGameState);
    }

    @Test
    void addSplitPlayerToGame_case3_createsNewSplitHand() {
        final String playerId = "P-001";
        final String NEW_PLAYER_ID = "P-002";

        Card cardOne = new Card(Suit.HEARTS, Rank.ACE);
        Card cardTwo = new Card(Suit.DIAMONDS, Rank.ACE);
        LinkedList<Card> handWithTwoCards = new LinkedList<>(List.of(cardOne, cardTwo));

        // 1.1 Mock Current Player (Az a kéz, ami betöltődik az ESET 3 előtt)
        PlayerHand mockCurrentPlayer = mock(PlayerHand.class);
        when(mockCurrentPlayer.getId()).thenReturn(playerId);
        when(mockCurrentPlayer.getHand()).thenReturn(handWithTwoCards);

        // 1.2 Mock Next Hand (Az a kéz, amit az ESET 3 kiválaszt a players map-ből)
        PlayerHand mockNextHandInMap = mock(PlayerHand.class);

        // 1.3 Players Map beállítása (tartalmazza az új kezet)
        Map<String, PlayerHand> playersMap = new HashMap<>();
        playersMap.put(NEW_PLAYER_ID, mockNextHandInMap);
        when(mockGameState.getPlayers()).thenReturn(playersMap);


        // 1.4 GameState feltételek beállítása
        when(mockGameState.getPlayer()).thenReturn(mockCurrentPlayer);
        when(mockGameState.getSplitPlayer()).thenReturn(null); // ESET 3: NINCS még split kéz

        // 1.5 Split Player másolatának mockolása
        PlayerHand mockNewSplitPlayer = mock(PlayerHand.class);
        when(gameStateManager.deepCopyPlayerHand(mockNextHandInMap)).thenReturn(mockNewSplitPlayer);

        // 1.6 Következő aktív játékos keresése (KRITIKUS: a kódnak tovább kell futnia)
        when(gameStateManager.findNextActivePlayerId(any())).thenReturn(Optional.of(NEW_PLAYER_ID));

        // 2. VÉGREHAJTÁS (Act)
        gameService.addSplitPlayerToGame(Optional.of(mockGameState));

        // 3. ELLENŐRZÉS (Verify)

        // A) ESET 3 Hívások Ellenőrzése (Split logika)
        // Megtörténik a players map-ből kivett kéz betöltése és a split kéz létrehozása
        verify(mockGameState, times(1)).setPlayer(mockNextHandInMap);
        verify(mockGameState, times(1)).setSplitPlayer(mockNewSplitPlayer);
        verify(gameStateManager, times(1)).deepCopyPlayerHand(mockNextHandInMap);
        verify(mockGameState, times(1)).addSplitReq(-1);
    }

}
