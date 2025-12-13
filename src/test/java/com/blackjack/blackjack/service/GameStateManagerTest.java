package com.blackjack.blackjack.service;

import com.blackjack.blackjack.factory.PlayerHandFactory;
import com.blackjack.blackjack.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GameStateManagerTest {
    private final Card testCardAce = new Card(Suit.HEARTS, Rank.ACE);
    private final Card testCardTen = new Card(Suit.HEARTS, Rank.TEN);
    private final Card mockDrawnCard = mock(Card.class);

    @InjectMocks
    private GameStateManager gameStateManager;
    @Mock
    private GameRuleEngine mockGameRuleEngine;
    @Mock
    private GameState mockGameState;
    @Mock
    private PlayerHand mockPlayerHand1;
    @Mock
    private PlayerHand mockPlayerHand2;
    @Mock
    private PlayerHand mockPlayerHand;
    @Mock
    private PlayerHandFactory mockPlayerHandFactory;
    @Mock
    private Deck mockDeck;
    @Mock
    private Map<String, Boolean> mockPlayersIndex;
    @Mock
    private LinkedList<Card> mockCurrentHand;
    @Mock
    private Card mockCardToSplit;

    private PlayerHand createMockHand(String id, boolean isStated) {
        PlayerHand hand = mock(PlayerHand.class);
        when(hand.getId()).thenReturn(id);
        when(hand.isStated()).thenReturn(isStated);
        return hand;
    }

    @Test
    void sortPlayersMap_shouldSortByStatedThenById() {
        // 1. Állapot: Stated=false, ID=A1 (Legmagasabb prioritású aktív játékos ID szerint)
        PlayerHand mockPlayerHandActiveA = mock(PlayerHand.class);
        when(mockPlayerHandActiveA.getId()).thenReturn("A1");
        when(mockPlayerHandActiveA.isStated()).thenReturn(false);

        // 2. Állapot: Stated=false, ID=B1 (Második prioritású aktív játékos ID szerint)
        PlayerHand mockPlayerHandActiveB = mock(PlayerHand.class);
        when(mockPlayerHandActiveB.getId()).thenReturn("B1");
        when(mockPlayerHandActiveB.isStated()).thenReturn(false);

        // 3. Állapot: Stated=true, ID=A2 (Befejezett kéz)
        PlayerHand mockPlayerHandStatedA = mock(PlayerHand.class);
        when(mockPlayerHandStatedA.getId()).thenReturn("A2");
        when(mockPlayerHandStatedA.isStated()).thenReturn(true);

        // Eredeti, rendezetlen Map (Fontos: Helyezzük be őket kaotikus sorrendben)
        Map<String, PlayerHand> playersMap = new LinkedHashMap<>();
        playersMap.put(mockPlayerHandStatedA.getId(), mockPlayerHandStatedA);   // A2, true
        playersMap.put(mockPlayerHandActiveB.getId(), mockPlayerHandActiveB);   // B1, false
        playersMap.put(mockPlayerHandActiveA.getId(), mockPlayerHandActiveA);   // A1, false

        gameStateManager.sortPlayersMap(playersMap);

        // Várt sorrend (false először, majd ID szerint: A1, B1, A2)
        List<PlayerHand> sortedList = new ArrayList<>(playersMap.values());

        // 1. Ellenőrzés: A (false, A1) az első
        assertEquals(3, sortedList.size());
        assertEquals("A1", sortedList.get(0).getId());
        assertFalse(sortedList.get(0).isStated());

        // 2. Ellenőrzés: A (false, B1) a második
        assertEquals("B1", sortedList.get(1).getId());
        assertFalse(sortedList.get(1).isStated());

        // 3. Ellenőrzés: A (true, A2) a harmadik
        assertEquals("A2", sortedList.get(2).getId());
        assertTrue(sortedList.get(2).isStated());
    }

    @Test
    void getGameStateOrThrow_shouldReturnGameStateWhenPresent() {
        Optional<GameState> presentGameState = Optional.of(mockGameState);

        GameState result = gameStateManager.getGameStateOrThrow(presentGameState);

        assertEquals(mockGameState, result);
    }

    @Test
    void getGameStateOrThrow_shouldThrowExceptionWhenEmpty() {
        Optional<GameState> emptyGameState = Optional.empty();

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> gameStateManager.getGameStateOrThrow(emptyGameState)
        );

        assertEquals("400 BAD_REQUEST \"CLIENT_ERROR_SPECIFIC\"", exception.getMessage());
    }

    @Test
    void resetRoundState_shouldResetAllStateFieldsAndClearPlayersMap() {
        // A PlayerHand.createEmptyHand() és hasonló metódusok statikusak,
        // és valódi objektumokat hoznak létre. Ezeket nem kell mockolni.

        // De a Map-nek mocknak kell lennie, hogy ellenőrizni tudjuk a .clear() hívást.
        // Mivel a GameState.getPlayers() egy Map-et ad vissza, ezt a Map-et is mockolnunk kell.
        Map<String, PlayerHand> mockPlayersMap = mock(Map.class);

        when(mockGameState.getPlayers()).thenReturn(mockPlayersMap);

        GameState result = gameStateManager.resetRoundState(mockGameState);

        // 1. Kéz- és állapotobjektumok visszaállítása (mocked metódus hívások ellenőrzése)
        verify(mockGameState, times(1)).setPlayer(any(PlayerHand.class));
        verify(mockGameState, times(1)).setDealerMasked(any(DealerHandMasked.class));
        verify(mockGameState, times(1)).setDealerUnmasked(any(DealerHandUnmasked.class));
        verify(mockGameState, times(1)).setSplitPlayer(any(PlayerHand.class));

        // 2. Primitív mezők visszaállítása
        verify(mockGameState, times(1)).setAces(false);
        verify(mockGameState, times(1)).setNatural21(0);
        verify(mockGameState, times(1)).setWinner(0);
        verify(mockGameState, times(1)).setHandCounter(0);
        verify(mockGameState, times(1)).setSplitReq(0);
        verify(mockGameState, times(1)).setRoundActive(false);

        // Ellenőrizzük, hogy a List.of() által létrehozott üres, immutable listával tér vissza.
        verify(mockGameState, times(1)).setBetList(eq(Collections.emptyList()));

        // Ellenőrizzük, hogy a getPlayers() által visszaadott Map-en meghívtuk a clear()-t.
        verify(mockPlayersMap, times(1)).clear();

        // A metódusnak a bemeneti (módosított) GameState-tel kell visszatérnie.
        assertEquals(mockGameState, result);
    }

    @Test
    void areStatedFalseHands_shouldReturnTrueIfAnyHandIsNotStated() {
        // Aktív kéz: isStated = false
        when(mockPlayerHand1.isStated()).thenReturn(false);

        // Befejezett kéz: isStated = true
        when(mockPlayerHand2.isStated()).thenReturn(true);

        Map<String, PlayerHand> playersMap = new LinkedHashMap<>();
        playersMap.put("A", mockPlayerHand2); // Befejezett
        playersMap.put("B", mockPlayerHand1); // Aktív

        boolean result = gameStateManager.areStatedFalseHands(playersMap);

        assertTrue(result);
    }

    @Test
    void areStatedFalseHands_shouldReturnFalseIfAllHandsAreStated() {
        // Kéz 1: isStated = true
        when(mockPlayerHand1.isStated()).thenReturn(true);

        // Kéz 2: isStated = true
        when(mockPlayerHand2.isStated()).thenReturn(true);

        Map<String, PlayerHand> playersMap = new LinkedHashMap<>();
        playersMap.put("A", mockPlayerHand1);
        playersMap.put("B", mockPlayerHand2);

        boolean result = gameStateManager.areStatedFalseHands(playersMap);

        assertFalse(result);
    }

    @Test
    void dealSplitCard_whenIsFirstIsTrueWithAceAce_shouldSetCanSplitFalse() {
        final int betAmount = 100;

        Card mockAce1 = mock(Card.class);
        when(mockAce1.rank()).thenReturn(Rank.ACE);
        Card mockAce2 = mock(Card.class);
        when(mockAce2.rank()).thenReturn(Rank.ACE);

        when(mockGameState.getPlayer()).thenReturn(mockPlayerHand);
        when(mockGameState.getDeck()).thenReturn(mockDeck);
        when(mockGameState.getBet()).thenReturn(betAmount);
        when(mockGameState.getPlayersIndex()).thenReturn(mockPlayersIndex);
        when(mockDeck.dealCard()).thenReturn(mockAce2);

        when(mockPlayerHand.getHand()).thenReturn(mockCurrentHand);
        when(mockPlayerHand.getId()).thenReturn("P1");

        when(mockGameRuleEngine.canSplit(mockCurrentHand)).thenReturn(true);

        when(mockCurrentHand.getFirst()).thenReturn(mockAce1);
        when(mockCurrentHand.getLast()).thenReturn(mockAce2);

        gameStateManager.dealSplitCard(mockGameState, true, mock(Card.class));

        verify(mockGameRuleEngine, times(1)).canSplit(mockCurrentHand);
        verify(mockPlayerHand, times(1)).setCanSplit(false);
        verify(mockPlayerHand, never()).setCanSplit(true);
    }

    @Test
    void dealSplitCard_whenIsFirstIsTrue_NoAceAce() {
        final int betAmount = 100;

        when(mockDrawnCard.rank()).thenReturn(Rank.EIGHT);
        when(mockGameState.getPlayer()).thenReturn(mockPlayerHand);
        when(mockGameState.getDeck()).thenReturn(mockDeck);
        when(mockGameState.getBet()).thenReturn(betAmount);
        when(mockGameState.getPlayersIndex()).thenReturn(mockPlayersIndex);

        when(mockDeck.dealCard()).thenReturn(mockDrawnCard);

        when(mockPlayerHand.getHand()).thenReturn(mockCurrentHand);
        when(mockPlayerHand.getId()).thenReturn("P1");
        when(mockCurrentHand.getFirst()).thenReturn(testCardTen);

        final boolean expectedSplitPossible = false;
        when(mockGameRuleEngine.canSplit(mockCurrentHand)).thenReturn(expectedSplitPossible);

        PlayerHand result = gameStateManager.dealSplitCard(mockGameState, true, mockCardToSplit);

        verify(mockDeck, times(1)).dealCard();
        verify(mockCurrentHand, times(1)).add(mockDrawnCard);
        verify(mockGameRuleEngine, times(1)).canSplit(mockCurrentHand);
        verify(mockPlayerHand, times(1)).setCanSplit(expectedSplitPossible);
        verify(mockPlayerHand, times(1)).setBet(betAmount);
        verify(mockPlayerHand, times(1)).setStated(false);
        verify(mockPlayersIndex, times(1)).put("P1", false);

        assertEquals(mockPlayerHand, result);
    }

    @Test
    void dealSplitCard_whenIsFirstIsFalse_createsNewHand() {
        final int betAmount = 100;
        final String newHandId = "P1-2";

        LinkedList<Card> mockNewHandCardList = mock(LinkedList.class);
        when(mockPlayerHandFactory.createEmptyHand()).thenReturn(mockPlayerHand2);
        when(mockPlayerHand2.getHand()).thenReturn(mockNewHandCardList);
        when(mockPlayerHand2.getId()).thenReturn(newHandId);

        when(mockGameState.getBet()).thenReturn(betAmount);
        when(mockGameState.getPlayersIndex()).thenReturn(mockPlayersIndex);
        when(mockGameState.generateNextHandId()).thenReturn(newHandId);

        PlayerHand result = gameStateManager.dealSplitCard(mockGameState, false, testCardTen);

        verify(mockPlayerHandFactory, times(1)).createEmptyHand();
        verify(mockGameState, times(1)).generateNextHandId();
        verify(mockPlayerHand2, times(1)).setId(newHandId);
        verify(mockNewHandCardList, times(1)).add(testCardTen);
        verify(mockPlayerHand2, times(1)).setBet(betAmount);
        verify(mockPlayersIndex, times(1)).put(newHandId, false);

        assertEquals(mockPlayerHand2, result);
    }

    @Test
    void areStatedFalseHands_withFalseState() {
        when(mockPlayerHand1.isStated()).thenReturn(true);
        when(mockPlayerHand2.isStated()).thenReturn(false);

        Map<String, PlayerHand> playersMap = new HashMap<>();
        playersMap.put("A", mockPlayerHand1);
        playersMap.put("B", mockPlayerHand2);
        boolean isThereFalseStatedHand = gameStateManager.areStatedFalseHands(playersMap);

        verify(mockPlayerHand1, times(1)).isStated();
        verify(mockPlayerHand2, times(1)).isStated();

        assertTrue(isThereFalseStatedHand);
    }

    @Test
    void findNextActivePlayerId_shouldReturnSmallestActiveId() {
        Map<String, Boolean> activePlayersMap = new HashMap<>();
        activePlayersMap.put("P-003", true);
        activePlayersMap.put("P-001", false);
        activePlayersMap.put("P-005", false);
        activePlayersMap.put("P-002", true);

        final String expectedId = "P-001";

        Optional<String> resultOptional = gameStateManager.findNextActivePlayerId(activePlayersMap);

        assertTrue(resultOptional.isPresent());
        assertEquals(expectedId, resultOptional.get());
    }

    @Test
    void findNextActivePlayerId_shouldReturnNoActiveId() {
        Map<String, Boolean> activePlayersMap = new HashMap<>();
        activePlayersMap.put("P-002", true);
        activePlayersMap.put("P-003", true);
        activePlayersMap.put("P-004", true);

        Optional<String> resultOptional = gameStateManager.findNextActivePlayerId(activePlayersMap);

        assertFalse(resultOptional.isPresent());
        assertEquals(Optional.empty(), resultOptional);
    }

    @Test
    void findNextActivePlayerId_shouldReturnEmptyOnNullOrEmptyMap() {
        mockPlayersIndex.clear();

        assertEquals(Optional.empty(), gameStateManager.findNextActivePlayerId(mockPlayersIndex));
        assertEquals(Optional.empty(), gameStateManager.findNextActivePlayerId(null));
    }

    @Test
    void deepCopyPlayerHand_shouldReturnNullForNullInput() {
        assertNull(gameStateManager.deepCopyPlayerHand(null));
    }

    @Test
    void deepCopyPlayerHand_shouldCreateIndependentCopy() {
        LinkedList<Card> originalHandList = new LinkedList<>();
        originalHandList.add(testCardAce);

        PlayerHand originalHand = PlayerHand.builder()
            .id("P1-1")
            .hand(originalHandList)
            .canSplit(true)
            .stated(false)
            .bet(100)
            .build();

        PlayerHand copiedHand = gameStateManager.deepCopyPlayerHand(originalHand);

        assertEquals(originalHand.getId(), copiedHand.getId());
        assertEquals(originalHand.isCanSplit(), copiedHand.isCanSplit());
        assertEquals(originalHand.isStated(), copiedHand.isStated());
        assertEquals(originalHand.getBet(), copiedHand.getBet());

        assertEquals(1, copiedHand.getHand().size());
        assertEquals(testCardAce, copiedHand.getHand().getFirst());

        originalHand.getHand().add(testCardTen);

        assertEquals(1, copiedHand.getHand().size());
        assertEquals(2, originalHand.getHand().size());
        assertNotSame(originalHand.getHand(), copiedHand.getHand());
    }

    @Test
    void sortPlayersMap_shouldOrderActiveHandsFirstThenById() {
        PlayerHand active_P2 = createMockHand("P-002", false); // Elvárt 2. hely
        PlayerHand active_P1 = createMockHand("P-001", false); // Elvárt 1. hely (ID alapján kisebb)
        PlayerHand stated_P4 = createMockHand("P-004", true);  // Elvárt 4. hely
        PlayerHand stated_P3 = createMockHand("P-003", true);  // Elvárt 3. hely (ID alapján kisebb)

        Map<String, PlayerHand> playersMap = new LinkedHashMap<>();
        playersMap.put(stated_P4.getId(), stated_P4);
        playersMap.put(active_P2.getId(), active_P2);
        playersMap.put(stated_P3.getId(), stated_P3);
        playersMap.put(active_P1.getId(), active_P1);

        List<String> expectedOrder = List.of(
            "P-001", // Active + kisebb ID
            "P-002", // Active + nagyobb ID
            "P-003", // Stated + kisebb ID
            "P-004"  // Stated + nagyobb ID
        );

        gameStateManager.sortPlayersMap(playersMap);

        assertEquals(4, playersMap.size());

        List<String> actualOrder = new ArrayList<>(playersMap.keySet());

        assertEquals(expectedOrder, actualOrder);

        verify(active_P1, times(2)).isStated();
        verify(stated_P4, atLeastOnce()).getId();
    }
}
