package com.blackjack.blackjack.service;

import com.blackjack.blackjack.factory.PlayerHandFactory;
import com.blackjack.blackjack.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameStateManagerTest {

    @Mock
    private GameRuleEngine gameRuleEngine;
    @Mock
    private PlayerHandFactory playerHandFactory;
    @Mock
    private HandValueCalculator handValueCalculator;
    @Mock
    private HandStateUpdater handStateUpdater;
    @Mock
    private DeckService deckService;

    @InjectMocks
    private GameStateManager gameStateManager;

    @Test
    @DisplayName("ResetRoundState: Minden mezőnek alaphelyzetbe kell állnia")
    void testResetRoundState() {
        GameState dirtyState = GameState.builder()
            .clientId(UUID.randomUUID())
            .isRoundActive(true)
            .bet(100)
            .betList(List.of(50, 50))
            .wasSplitInRound(true)
            .splitReq(1)
            .winner(1)
            .natural21(1)
            .handCounter(5)
            .players(Map.of("P-001", PlayerHand.builder().build()))
            .build();

        GameState cleanState = gameStateManager.resetRoundState(dirtyState);

        assertAll("A kör resetelése utáni állapot ellenőrzése",
            () -> assertFalse(cleanState.isRoundActive(), "A körnek inaktívnak kell lennie"),
            () -> assertFalse(cleanState.isWasSplitInRound(), "A split flagnak hamisnak kell lennie"),
            () -> assertEquals(0, cleanState.getSplitReq(), "A splitReq-nek 0-nak kell lennie"),
            () -> assertEquals(0, cleanState.getWinner(), "A winnernek 0-nak kell lennie"),
            () -> assertEquals(0, cleanState.getHandCounter(), "A handCounternek 0-nak kell lennie"),
            () -> {
                assertNotNull(cleanState.getBetList());
                assertTrue(cleanState.getBetList().isEmpty(), "A betList-nek üresnek kell lennie");
            },
            () -> assertTrue(cleanState.getPlayers().isEmpty(), "A players map-nek üresnek kell lennie"),

            // Ellenőrizzük, hogy a kezek üresek-e (factory/static create metódusok alapján)
            () -> assertNotNull(cleanState.getPlayer(), "A player hand nem lehet null"),
            () -> assertNotNull(cleanState.getDealerMasked(), "A dealer hand nem lehet null"),
            () -> assertEquals(dirtyState.getClientId(), cleanState.getClientId(), "A clientId-nak meg kell maradnia")
        );
    }

    @Test
    @DisplayName("createNewDeck tesztelése: A szerviztől kapott új pakli bekerül a state-be")
    void testCreateNewDeck() {
        GameState initialState = GameState.builder()
            .deck(null)
            .build();

        // Létrehozunk egy valódi Deck példányt a statikus factory-val
        // (Mivel a Deck rekord és egyszerű, használhatunk valódi példányt a mock helyett is,
        // de a deckService-t mindenképpen mockoljuk)
        Deck newDeckFromService = Deck.createNewDeck();

        when(deckService.createNewDeck()).thenReturn(newDeckFromService);

        GameState updatedState = gameStateManager.createNewDeck(initialState);

        assertNotNull(updatedState.getDeck(), "Az új állapotban kell lennie paklinak");
        assertEquals(newDeckFromService, updatedState.getDeck(), "A state-ben pontosan a kapott paklinak kell lennie");
        assertEquals(newDeckFromService.getDeckLength(), updatedState.getDeck().getDeckLength(), "A pakli hossza is egyezzen");
        assertSame(newDeckFromService, updatedState.getDeck(), "Ugyanazt a példányt kell tartalmaznia");

        verify(deckService, times(1)).createNewDeck();
    }

    @Test
    @DisplayName("safeDealCard: Sima osztás, amikor van elég lap")
    void testSafeDealCard_NormalCase() {
        Card expectedCard = new Card(Suit.SPADES, Rank.ACE);
        Deck initialDeck = Deck.builder().deck(List.of(expectedCard)).build();
        Deck remainingDeck = Deck.builder().deck(List.of()).build();

        GameState state = GameState.builder().deck(initialDeck).build();

        // Amikor a deckService.dealCard-ot hívjuk, adjon vissza egy lapot és az új paklit
        when(deckService.dealCard(initialDeck)).thenReturn(new DealResult(expectedCard, remainingDeck));

        GameService.DealResultWithState resultWithState = gameStateManager.safeDealCard(state);

        assertEquals(expectedCard, resultWithState.dealtCard());
        assertEquals(remainingDeck, resultWithState.newGameState().getDeck());
        verify(deckService, times(1)).dealCard(any());
        verify(deckService, never()).createNewDeck(); // Itt nem kell új pakli
    }

    @Test
    @DisplayName("safeDealCard: Automata újrakeverés, ha a pakli üres vagy hibát dob")
    void testSafeDealCard_ReshuffleCase() {
        GameState state = GameState.builder().deck(null).build(); // Nincs pakli

        Card expectedCard = new Card(Suit.HEARTS, Rank.KING);
        Deck newFullDeck = Deck.createNewDeck();
        Deck afterDealDeck = Deck.builder().deck(List.of()).build();

        when(deckService.createNewDeck()).thenReturn(newFullDeck);
        when(deckService.dealCard(newFullDeck)).thenReturn(new DealResult(expectedCard, afterDealDeck));

        GameService.DealResultWithState resultWithState = gameStateManager.safeDealCard(state);

        assertEquals(expectedCard, resultWithState.dealtCard());
        assertEquals(afterDealDeck, resultWithState.newGameState().getDeck());

        verify(deckService, atLeastOnce()).createNewDeck();
    }

    @Test
    @DisplayName("processDealerDrawing: A Dealer 13-ról indulva, 16-on át 20-nál áll meg")
    void testProcessDealerDrawing_Success() {
        Card c8 = new Card(Suit.CLUBS, Rank.EIGHT);
        Card c5 = new Card(Suit.DIAMONDS, Rank.FIVE); // 13 pont

        DealerHandUnmasked initialDealerHand = DealerHandUnmasked.builder()
            .hand(List.of(c8, c5))
            .sum(13)
            .handState(HandState.UNDER_21.getValue())
            .build();

        GameState initialState = GameState.builder()
            .dealerUnmasked(initialDealerHand)
            .deck(Deck.builder().deck(List.of()).build())
            .build();

        Card draw1 = new Card(Suit.HEARTS, Rank.THREE); // 13 + 3 = 16
        Card draw2 = new Card(Suit.SPADES, Rank.FOUR);  // 16 + 4 = 20

        // Mockoljuk a deckService-t: két egymást követő osztást szimulálunk
        when(deckService.dealCard(any()))
            .thenReturn(new DealResult(draw1, Deck.builder().deck(List.of()).build()))
            .thenReturn(new DealResult(draw2, Deck.builder().deck(List.of()).build()));

        when(handValueCalculator.calculateSum(anyList())).thenAnswer(invocation -> {
            List<Card> cards = invocation.getArgument(0);
            return cards.size() == 2 ? 13 : (cards.size() == 3 ? 16 : 20);
        });

        // Az updater mindig UNDER_21-et ad vissza (nem bustol be a dealer)
        when(handStateUpdater.updateHandState(anyInt(), anyInt(), anyBoolean()))
            .thenReturn(HandState.UNDER_21);

        GameState finalState = gameStateManager.processDealerDrawing(initialState);

        DealerHandUnmasked finalHand = finalState.getDealerUnmasked();
        assertNotNull(finalHand);

        assertEquals(20, finalHand.sum(), "A Dealernek 20 pontnál kell megállnia.");
        assertEquals(4, finalHand.hand().size(), "Összesen 4 lapnak kell lennie a kézben.");

        verify(deckService, times(2)).dealCard(any());
    }

    @Test
    @DisplayName("dealSplitCard: Teljes split folyamat tesztelése (Első kéz frissítése + Második létrehozása)")
    void testFullSplitFlow() {
        Card card8S = new Card(Suit.SPADES, Rank.EIGHT);
        Card drawForFirst = new Card(Suit.HEARTS, Rank.TEN); // Első kéz kap egy 10-est -> 18

        PlayerHand originalHand = PlayerHand.builder()
            .id("P-001")
            .hand(List.of(card8S)) // A split utáni állapot: már csak egy lap van itt
            .bet(100)
            .build();

        GameState state = GameState.builder()
            .player(originalHand)
            .deck(Deck.builder().deck(List.of(drawForFirst)).build())
            .players(new HashMap<>())
            .playersIndex(new LinkedHashMap<>())
            .handCounter(1)
            .build();

        when(gameRuleEngine.canSplit(anyList())).thenReturn(false);
        when(deckService.dealCard(any())).thenReturn(new DealResult(drawForFirst, Deck.builder().deck(List.of()).build()));
        when(handValueCalculator.calculateSum(anyList())).thenReturn(18); // 8+10
        when(handStateUpdater.updateHandState(anyInt(), anyInt(), anyBoolean())).thenReturn(HandState.UNDER_21);

        GameState stateAfterFirst = gameStateManager.dealSplitCard(state, true, null);

        assertNotNull(stateAfterFirst.getPlayer());
        assertEquals(18, stateAfterFirst.getPlayer().sum());
        assertEquals(2, stateAfterFirst.getPlayer().hand().size());

        Card card8D = new Card(Suit.DIAMONDS, Rank.EIGHT);
        when(playerHandFactory.createEmptyHand()).thenReturn(PlayerHand.builder().build());

        GameState finalState = gameStateManager.dealSplitCard(stateAfterFirst, false, card8D);

        assertEquals(2, finalState.getPlayers().size() + 1); // Player + Players map
        assertTrue(finalState.getPlayers().containsKey("P-002"));
        assertEquals(100, finalState.getPlayers().get("P-002").bet(), "Az új kéznek is 100-as tétje kell legyen");
        assertEquals(2, finalState.getHandCounter());
    }

    @Test
    @DisplayName("dealSplitCard - isFirst TRUE: Csak az első kéz kap új lapot")
    void testDealSplitCard_FirstHandDraw() {
        Card deckCard = new Card(Suit.HEARTS, Rank.TEN);
        PlayerHand player = PlayerHand.builder().id("P-001").hand(List.of(new Card(Suit.SPADES, Rank.EIGHT))).build();
        GameState state = GameState.builder()
            .player(player)
            .deck(Deck.builder().deck(List.of(deckCard)).build())
            .playersIndex(new LinkedHashMap<>())
            .build();

        when(gameRuleEngine.canSplit(anyList())).thenReturn(false);
        when(deckService.dealCard(any())).thenReturn(new DealResult(deckCard, Deck.builder().deck(List.of()).build()));
        when(handValueCalculator.calculateSum(any())).thenReturn(18);
        when(handStateUpdater.updateHandState(anyInt(), anyInt(), anyBoolean())).thenReturn(HandState.UNDER_21);

        GameState result = gameStateManager.dealSplitCard(state, true, null);

        assertNotNull(result.getPlayer());
        assertEquals(2, result.getPlayer().hand().size());
        assertEquals(deckCard, result.getPlayer().hand().get(1));
        verify(deckService, times(1)).dealCard(any());
    }

    @Test
    @DisplayName("dealSplitCard - isFirst FALSE: Új kéz jön létre a splitelt lappal")
    void testDealSplitCard_SecondHandCreation() {
        Card splitCard = new Card(Suit.DIAMONDS, Rank.EIGHT);
        PlayerHand originalPlayer = PlayerHand.builder().id("P-001").bet(500).build();
        GameState state = GameState.builder()
            .player(originalPlayer)
            .handCounter(1)
            .players(new HashMap<>())
            .playersIndex(new LinkedHashMap<>())
            .build();

        when(playerHandFactory.createEmptyHand()).thenReturn(PlayerHand.builder().build());

        GameState result = gameStateManager.dealSplitCard(state, false, splitCard);

        String newId = "P-002";
        assertTrue(result.getPlayers().containsKey(newId));
        assertEquals(500, result.getPlayers().get(newId).bet(), "A tétnek egyeznie kell");
        assertEquals(splitCard, result.getPlayers().get(newId).hand().getFirst());
        assertEquals(2, result.getHandCounter());
    }

    @Test
    @DisplayName("findNextActivePlayerId: Megtalálja a legkisebb ID-jú aktív játékost")
    void testFindNextActivePlayerId_ReturnsFirstFalse() {
        Map<String, Boolean> playersIndex = new LinkedHashMap<>();
        playersIndex.put("P-001", true);
        playersIndex.put("P-003", false);
        playersIndex.put("P-002", false);

        Optional<String> result = gameStateManager.findNextActivePlayerId(playersIndex);

        assertTrue(result.isPresent());
        assertEquals("P-002", result.get());
    }

    @Test
    @DisplayName("findNextActivePlayerId: Empty-t ad vissza, ha mindenki kész")
    void testFindNextActivePlayerId_ReturnsEmptyWhenAllFinished() {
        Map<String, Boolean> playersIndex = Map.of(
            "P-001", true,
            "P-002", true
        );

        Optional<String> result = gameStateManager.findNextActivePlayerId(playersIndex);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("areStatedFalseHands: True-t ad, ha van legalább egy aktív (false) kéz")
    void testAreStatedFalseHands_WhenActiveHandExists() {
        PlayerHand hand1 = PlayerHand.builder().id("P-001").stated(true).build();  // Kész
        PlayerHand hand2 = PlayerHand.builder().id("P-002").stated(false).build(); // AKTÍV

        Map<String, PlayerHand> playersMap = Map.of(
            "P-001", hand1,
            "P-002", hand2
        );

        boolean result = gameStateManager.areStatedFalseHands(playersMap);

        assertTrue(result, "True-nak kell lennie, mert P-002 még aktív.");
    }

    @Test
    @DisplayName("areStatedFalseHands: False-t ad, ha minden kéz lezárult (true)")
    void testAreStatedFalseHands_WhenAllHandsAreStated() {
        Map<String, PlayerHand> playersMap = Map.of(
            "P-001", PlayerHand.builder().stated(true).build(),
            "P-002", PlayerHand.builder().stated(true).build()
        );

        boolean result = gameStateManager.areStatedFalseHands(playersMap);

        assertFalse(result, "False-nak kell lennie, mert mindenki végzett.");
    }

    @Test
    @DisplayName("areStatedFalseHands: False-t ad üres map esetén")
    void testAreStatedFalseHands_EmptyMap() {
        assertFalse(gameStateManager.areStatedFalseHands(new HashMap<>()));
        assertFalse(gameStateManager.areStatedFalseHands(null));
    }
}
