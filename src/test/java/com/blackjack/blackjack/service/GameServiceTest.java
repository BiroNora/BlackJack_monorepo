package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static com.blackjack.blackjack.common.GameConstants.INITIAL_DECK_LENGTH;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GameServiceTest {

    @Mock
    private GameRuleEngine gameRuleEngine;
    @Mock
    private GameStateManager gameStateManager;
    @Mock
    private HandValueCalculator handValueCalculator;
    @Mock
    private HandStateUpdater handStateUpdater;

    @InjectMocks
    private GameService gameService;

    @Test
    @DisplayName("initializeNewRound: Sikeres alap osztás (nincs Blackjack)")
    void testInitializeNewRound_Success() {
        GameState startState = GameState.builder()
            .bet(100)
            .player(PlayerHand.builder().build())
            .dealerUnmasked(DealerHandUnmasked.builder().build())
            .dealerMasked(DealerHandMasked.builder().build())
            .build();

        Card card1 = new Card(Suit.SPADES, Rank.EIGHT); // Player 1
        Card card2 = new Card(Suit.HEARTS, Rank.FIVE);  // Dealer 1
        Card card3 = new Card(Suit.CLUBS, Rank.TEN);    // Player 2
        Card card4 = new Card(Suit.DIAMONDS, Rank.ACE); // Dealer 2 (Látható lap)

        when(gameStateManager.resetRoundState(any())).thenReturn(startState);

        when(gameStateManager.safeDealCard(any()))
            .thenReturn(new GameService.DealResultWithState(card1, startState))
            .thenReturn(new GameService.DealResultWithState(card2, startState))
            .thenReturn(new GameService.DealResultWithState(card3, startState))
            .thenReturn(new GameService.DealResultWithState(card4, startState));

        when(handValueCalculator.calculateSum(anyList())).thenReturn(18); // Player sum
        when(handStateUpdater.updateHandState(anyInt(), anyInt(), anyBoolean())).thenReturn(HandState.UNDER_21);
        when(gameRuleEngine.canSplit(anyList())).thenReturn(false);
        when(gameRuleEngine.initNatural21State(any())).thenReturn(WinnerState.NONE.getValue());

        GameState result = gameService.initializeNewRound(startState);

        assertNotNull(result);
        assertTrue(result.isRoundActive());
        assertNotNull(result.getPlayer());
        assertEquals(18, result.getPlayer().sum());
        assertEquals("P-001", result.getPlayer().id());

        // Dealer ellenőrzése (card4 az ász, tehát tud biztosítást kötni)
        assertNotNull(result.getDealerMasked());
        assertTrue(result.getDealerMasked().canInsure());
        assertEquals(card4, result.getDealerMasked().hand().get(1)); // A második lap a látható
        Card maskedCard = result.getDealerMasked().hand().get(0);
        assertEquals("✪", maskedCard.rank().getSymbol());
        assertEquals(" ", maskedCard.suit().getSymbol());

        verify(gameStateManager, times(4)).safeDealCard(any());
    }

    @Test
    @DisplayName("initializeNewRound: Player Blackjack szituáció tesztelése")
    void testInitializeNewRound_PlayerBlackjack() {
        GameState startState = GameState.builder()
            .bet(100)
            .player(PlayerHand.builder().build())
            .dealerUnmasked(DealerHandUnmasked.builder().build())
            .dealerMasked(DealerHandMasked.builder().build())
            .build();

        // Player lapjai: Ász (card1) és King (card3) -> Blackjack
        Card card1 = new Card(Suit.SPADES, Rank.ACE);
        Card card2 = new Card(Suit.HEARTS, Rank.FIVE);
        Card card3 = new Card(Suit.CLUBS, Rank.KING);
        Card card4 = new Card(Suit.DIAMONDS, Rank.EIGHT);

        when(gameStateManager.resetRoundState(any())).thenReturn(startState);

        when(gameStateManager.safeDealCard(any()))
            .thenReturn(new GameService.DealResultWithState(card1, startState))
            .thenReturn(new GameService.DealResultWithState(card2, startState))
            .thenReturn(new GameService.DealResultWithState(card3, startState))
            .thenReturn(new GameService.DealResultWithState(card4, startState));

        when(handValueCalculator.calculateSum(anyList())).thenReturn(21, 13); // 21 (Player), 13 (Dealer)

        // A Player állapota BLACKJACK lesz
        when(handStateUpdater.updateHandState(anyInt(), anyInt(), anyBoolean()))
            .thenReturn(HandState.BLACKJACK) // Első hívás (Player)
            .thenReturn(HandState.UNDER_21); // Második hívás (Dealer)

        when(gameRuleEngine.canSplit(anyList())).thenReturn(false);
        when(gameRuleEngine.initNatural21State(any())).thenReturn(WinnerState.BLACKJACK_PLAYER_WON.getValue());

        GameState result = gameService.initializeNewRound(startState);

        assertNotNull(result);
        assertEquals(WinnerState.BLACKJACK_PLAYER_WON.getValue(), result.getNatural21());

        assertNotNull(result.getPlayer());
        assertEquals(21, result.getPlayer().sum());
        assertEquals(HandState.BLACKJACK.getValue(), result.getPlayer().handState());

        assertNotNull(result.getDealerMasked());
        assertFalse(result.getDealerMasked().canInsure(), "8-asra nem lehet biztosítást kötni");
        assertEquals(card4, result.getDealerMasked().hand().get(1), "A látható lap a 8-as");

        assertFalse(result.isAces());

        verify(gameRuleEngine, times(1)).initNatural21State(any());
    }

    @Test
    @DisplayName("initializeNewRound: Split lehetőség felismerése (két 8-as)")
    void testInitializeNewRound_SplitPossible() {
        GameState startState = GameState.builder()
            .bet(100)
            .player(PlayerHand.builder().build())
            .dealerUnmasked(DealerHandUnmasked.builder().build())
            .dealerMasked(DealerHandMasked.builder().build())
            .build();

        Card p1 = new Card(Suit.SPADES, Rank.EIGHT);   // Player első lapja
        Card d1 = new Card(Suit.HEARTS, Rank.FIVE);    // Dealer első lapja (rejtett)
        Card p2 = new Card(Suit.CLUBS, Rank.EIGHT);    // Player második lapja (PÁR!)
        Card d2 = new Card(Suit.DIAMONDS, Rank.TEN);   // Dealer második lapja (látható)

        when(gameStateManager.resetRoundState(any())).thenReturn(startState);

        when(gameStateManager.safeDealCard(any()))
            .thenReturn(new GameService.DealResultWithState(p1, startState))
            .thenReturn(new GameService.DealResultWithState(d1, startState))
            .thenReturn(new GameService.DealResultWithState(p2, startState))
            .thenReturn(new GameService.DealResultWithState(d2, startState));

        when(handValueCalculator.calculateSum(anyList())).thenReturn(16, 15);
        when(handStateUpdater.updateHandState(anyInt(), anyInt(), anyBoolean()))
            .thenReturn(HandState.UNDER_21);
        when(gameRuleEngine.canSplit(anyList())).thenReturn(true);
        when(gameRuleEngine.initNatural21State(any())).thenReturn(WinnerState.NONE.getValue());

        GameState result = gameService.initializeNewRound(startState);

        assertNotNull(result);
        assertNotNull(result.getPlayer());

        assertEquals(2, result.getPlayer().hand().size(), "A játékosnak 2 lapja kell legyen.");
        assertEquals(Rank.EIGHT, result.getPlayer().hand().get(0).rank(), "Az első lapnak 8-asnak kell lennie.");
        assertEquals(Rank.EIGHT, result.getPlayer().hand().get(1).rank(), "A második lapnak 8-asnak kell lennie.");

        assertTrue(result.getPlayer().canSplit(), "A 'canSplit' flagnek true-nak kell lennie a két 8-as után.");

        assertNotNull(result.getDealerMasked());
        assertEquals(d2, result.getDealerMasked().hand().get(1), "A dealer látható lapja a 10-es kell legyen.");
        assertFalse(result.getDealerMasked().canInsure(), "10-es lapra nem lehet biztosítást kötni.");

        verify(gameStateManager, times(4)).safeDealCard(any());
        verify(gameRuleEngine, times(1)).canSplit(anyList());
    }

    @Test
    @DisplayName("playerHit: Sikeres lapkérés (Hit), 21 alatt marad")
    void testPlayerHit_Success_Under21() {
        Card initialCard = new Card(Suit.SPADES, Rank.EIGHT);
        PlayerHand initialPlayer = PlayerHand.builder()
            .hand(List.of(initialCard))
            .sum(8)
            .handState(HandState.UNDER_21.getValue())
            .build();

        GameState currentState = GameState.builder()
            .isRoundActive(true)
            .player(initialPlayer)
            .build();

        // A következő kártya, amit húzni fog (egy 7-es)
        Card nextCard = new Card(Suit.HEARTS, Rank.SEVEN);

        when(gameStateManager.safeDealCard(currentState))
            .thenReturn(new GameService.DealResultWithState(nextCard, currentState));
        when(handValueCalculator.calculateSum(anyList())).thenReturn(15);
        when(handStateUpdater.updateHandState(anyInt(), anyInt(), anyBoolean()))
            .thenReturn(HandState.UNDER_21);
        GameState result = gameService.playerHit(currentState);

        assertNotNull(result);
        assertEquals(2, result.getPlayer().hand().size());
        assertEquals(15, result.getPlayer().sum());
        assertEquals(HandState.UNDER_21.getValue(), result.getPlayer().handState());

        assertEquals(Rank.EIGHT, result.getPlayer().hand().get(0).rank());
        assertEquals(Rank.SEVEN, result.getPlayer().hand().get(1).rank());

        verify(gameStateManager, times(1)).safeDealCard(any());
    }

    @Test
    @DisplayName("playerHit: Besokallás (Bust) tesztelése")
    void testPlayerHit_Bust() {
        Card c1 = new Card(Suit.SPADES, Rank.TEN);
        Card c2 = new Card(Suit.HEARTS, Rank.SIX);
        PlayerHand player = PlayerHand.builder()
            .hand(List.of(c1, c2))
            .sum(16)
            .handState(HandState.UNDER_21.getValue())
            .build();

        GameState state = GameState.builder()
            .isRoundActive(true)
            .player(player)
            .build();

        Card bustCard = new Card(Suit.CLUBS, Rank.SEVEN); // 16 + 7 = 23 (Bust)

        when(gameStateManager.safeDealCard(state))
            .thenReturn(new GameService.DealResultWithState(bustCard, state));
        when(handValueCalculator.calculateSum(anyList())).thenReturn(23);
        when(handStateUpdater.updateHandState(eq(23), anyInt(), anyBoolean()))
            .thenReturn(HandState.BUST);

        GameState result = gameService.playerHit(state);

        assertEquals(23, result.getPlayer().sum());
        assertEquals(HandState.BUST.getValue(), result.getPlayer().handState());
        assertEquals(3, result.getPlayer().hand().size());
    }

    @Test
    @DisplayName("playerHit: Inaktív kör esetén nem történik kártyaosztás")
    void testPlayerHit_InactiveRound() {
        GameState inactiveState = GameState.builder()
            .isRoundActive(false)
            .build();

        GameState result = gameService.playerHit(inactiveState);

        assertSame(inactiveState, result, "Inaktív körnél az eredeti állapotot kell visszakapni");

        verifyNoInteractions(gameStateManager);
        verifyNoInteractions(handValueCalculator);
    }

    @Test
    @DisplayName("playerHit: Split utáni kártyahúzás")
    void testPlayerHit_AfterSplit() {
        GameState splitState = GameState.builder()
            .isRoundActive(true)
            .wasSplitInRound(true) // <--- Jelözzük, hogy volt split
            .player(PlayerHand.builder().hand(new ArrayList<>()).build())
            .build();

        Card card = new Card(Suit.DIAMONDS, Rank.ACE);
        when(gameStateManager.safeDealCard(splitState))
            .thenReturn(new GameService.DealResultWithState(card, splitState));

        when(handValueCalculator.calculateSum(anyList())).thenReturn(11);
        when(handStateUpdater.updateHandState(anyInt(), anyInt(), eq(true)))
            .thenReturn(HandState.UNDER_21);

        gameService.playerHit(splitState);

        verify(handStateUpdater).updateHandState(eq(11), eq(1), eq(true));
    }

    @Test
    @DisplayName("playerStand: Normál megállás (18), Dealer húz és nyer")
    void testPlayerStand_NormalFlow() {
        PlayerHand player = PlayerHand.builder()
            .sum(18)
            .hand(List.of(new Card(Suit.SPADES, Rank.TEN), new Card(Suit.HEARTS, Rank.EIGHT)))
            .stated(false)
            .build();

        GameState state = GameState.builder()
            .isRoundActive(true)
            .player(player)
            .wasSplitInRound(false) // <--- NINCS SPLIT
            .build();

        when(handStateUpdater.updateHandState(eq(18), anyInt(), eq(false)))
            .thenReturn(HandState.UNDER_21);

        DealerHandUnmasked dealerFinal = DealerHandUnmasked.builder().sum(19).build();

        GameState stateAfterDealer = state.toBuilder()
            .player(player.toBuilder().stated(true).handState(HandState.UNDER_21.getValue()).build())
            .dealerUnmasked(dealerFinal)
            .build();

        when(gameStateManager.processDealerDrawing(any())).thenReturn(stateAfterDealer);
        when(gameRuleEngine.winnerStateUpdater(any(), any())).thenReturn(1);

        GameState result = gameService.playerStand(state);

        assertNotNull(result);
        assertEquals(1, result.getWinner());
        verify(gameStateManager, times(1)).processDealerDrawing(any());

        assertNotNull(result.getDealerUnmasked());
        assertEquals(19, result.getDealerUnmasked().sum());
    }

    @Test
    @DisplayName("calculateRewards: Játékos Blackjackje esetén 2.5-szeres szorzó")
    void testCalculateRewards_PlayerBlackjack() {
        PlayerHand player = PlayerHand.builder().bet(100).build();
        DealerHandUnmasked dealer = DealerHandUnmasked.builder().natural21(0).build();

        GameState state = GameState.builder()
            .player(player)
            .dealerUnmasked(dealer)
            .natural21(WinnerState.BLACKJACK_PLAYER_WON.getValue()) // BJ állapot
            .winner(WinnerState.PLAYER_WON.getValue())
            .players(Collections.emptyMap()) // Nincs több splitelt kéz
            .build();

        GameService.TransactionResult result = gameService.calculateRewards(state);

        assertEquals(250, result.transactionResult(), "100-as tétnél 250-es kifizetés jár Blackjackre.");
        assertEquals(0, result.gameState().getBet(), "A globális tétet le kell nullázni.");
        assertNotNull(result.gameState().getPlayer());
        assertEquals(0, result.gameState().getPlayer().bet(), "A kéz tétjét le kell nullázni.");
        assertFalse(result.gameState().isRoundActive(), "Ha nincs több kéz a players listában, az aktív kör véget ér.");
    }

    @Test
    @DisplayName("calculateRewards: Sima győzelem esetén 2.0-szeres szorzó")
    void testCalculateRewards_PlayerWon() {
        PlayerHand player = PlayerHand.builder().bet(100).build();
        DealerHandUnmasked dealer = DealerHandUnmasked.builder().natural21(0).build();

        GameState state = GameState.builder()
            .player(player)
            .dealerUnmasked(dealer)
            .natural21(0) // Nem Blackjack
            .winner(WinnerState.PLAYER_WON.getValue())
            .players(Collections.emptyMap())
            .build();

        GameService.TransactionResult result = gameService.calculateRewards(state);

        assertEquals(200, result.transactionResult(), "100-as tétnél 200-as kifizetés jár sima győzelemre.");
    }

    @Test
    @DisplayName("calculateRewards: Döntetlen (Push) esetén a tét visszajár")
    void testCalculateRewards_Push() {
        PlayerHand player = PlayerHand.builder().bet(100).build();
        DealerHandUnmasked dealer = DealerHandUnmasked.builder().natural21(0).build();

        GameState state = GameState.builder()
            .player(player)
            .dealerUnmasked(dealer)
            .winner(WinnerState.PUSH.getValue())
            .players(Collections.emptyMap())
            .build();

        GameService.TransactionResult result = gameService.calculateRewards(state);

        assertEquals(100, result.transactionResult(), "Döntetlen esetén a 100-as tétnek vissza kell járnia.");
    }

    @Test
    @DisplayName("calculateRewards: Játékos veszít, 0 kifizetés")
    void testCalculateRewards_PlayerLost() {
        PlayerHand player = PlayerHand.builder()
            .bet(100) // Volt 100-as tétje
            .build();

        DealerHandUnmasked dealer = DealerHandUnmasked.builder()
            .natural21(0)
            .build();

        GameState state = GameState.builder()
            .player(player)
            .dealerUnmasked(dealer)
            .winner(WinnerState.DEALER_WON.getValue()) // A dealer nyert
            .players(Collections.emptyMap())
            .build();

        GameService.TransactionResult result = gameService.calculateRewards(state);

        assertEquals(0, result.transactionResult(), "Veszteség esetén a kifizetés 0.");

        assertNotNull(result.gameState().getPlayer());
        assertEquals(0, result.gameState().getPlayer().bet());
        assertEquals(0, result.gameState().getBet());
    }

    @Test
    @DisplayName("applyDoubleBet: A tét megduplázódik a GameState-ben és a Player-nél is")
    void testApplyDoubleBet_Success() {
        int originalBet = 100;

        PlayerHand initialPlayer = PlayerHand.builder()
            .bet(originalBet) // Már van benne 100
            .build();

        GameState initialState = GameState.builder()
            .bet(originalBet) // A globális tét is 100
            .player(initialPlayer)
            .build();

        GameState result = gameService.applyDoubleBet(initialState, originalBet);

        assertNotNull(result);
        assertEquals(200, result.getBet(), "A globális tétnek 200-nak kell lennie.");
        assertNotNull(result.getPlayer());
        assertEquals(200, result.getPlayer().bet(), "A játékos kezében lévő tétnek is 200-nak kell lennie.");
        assertNotSame(initialState, result);
        assertNotSame(initialPlayer, result.getPlayer());
    }

    @Test
    @DisplayName("insuranceRequest: Dealernek BJ van - a játékos visszakapja az eredeti tétet")
    void testInsuranceRequest_DealerHasBlackjack() {
        int initialBet = 100;
        PlayerHand player = PlayerHand.builder().bet(initialBet).build();

        DealerHandUnmasked dealer = DealerHandUnmasked.builder()
            .natural21(WinnerState.BLACKJACK_DEALER_WON.getValue())
            .build();

        GameState state = GameState.builder()
            .bet(initialBet)
            .player(player)
            .dealerUnmasked(dealer)
            .isRoundActive(true)
            .build();

        GameService.TransactionResult result = gameService.insuranceRequest(state);

        assertEquals(100, result.transactionResult());
        assertEquals(0, result.gameState().getBet());
        assertFalse(result.gameState().isRoundActive());
        assertEquals(WinnerState.DEALER_WON.getValue(), result.gameState().getWinner());
    }

    @Test
    @DisplayName("insuranceRequest: Nincs Dealer BJ - a biztosítás ára levonásra kerül")
    void testInsuranceRequest_NoDealerBlackjack() {
        int initialBet = 100; // Biztosítás ára: 100 / 2 = 50
        PlayerHand player = PlayerHand.builder().bet(initialBet).build();

        DealerHandUnmasked dealer = DealerHandUnmasked.builder().natural21(0).build();

        GameState state = GameState.builder()
            .bet(initialBet)
            .player(player)
            .dealerUnmasked(dealer)
            .isRoundActive(true)
            .build();

        GameService.TransactionResult result = gameService.insuranceRequest(state);

        assertEquals(-50, result.transactionResult());
        assertEquals(100, result.gameState().getBet());
        assertTrue(result.gameState().isRoundActive());
    }

    @Test
    @DisplayName("splitHand: Sikeres split és két új lap osztása")
    void testSplitHand_Success() {
        Card card1 = new Card(Suit.SPADES, Rank.EIGHT);
        Card card2 = new Card(Suit.HEARTS, Rank.EIGHT);

        PlayerHand originalHand = PlayerHand.builder()
            .id("hand-1")
            .hand(new ArrayList<>(List.of(card1, card2))) // Két 8-as
            .canSplit(true)
            .build();

        GameState initialState = GameState.builder()
            .player(originalHand)
            .players(new HashMap<>())
            .splitReq(0)
            .wasSplitInRound(false)
            .build();

        GameState stateAfterFirst = initialState.toBuilder().wasSplitInRound(true).build();
        GameState finalState = stateAfterFirst.toBuilder().build();

        when(gameStateManager.dealSplitCard(any(GameState.class), eq(true), isNull()))
            .thenReturn(stateAfterFirst);
        when(gameStateManager.dealSplitCard(any(GameState.class), eq(false), eq(card2)))
            .thenReturn(finalState);

        GameState result = gameService.splitHand(initialState);

        assertNotNull(result);
        assertTrue(result.isWasSplitInRound(), "A wasSplitInRound flagnek true-nak kell lennie.");

        verify(gameStateManager).dealSplitCard(argThat(s -> {
            assertNotNull(s.getPlayer());
            return s.getPlayer().hand().size() == 1;
        }), eq(true), isNull());
        verify(gameStateManager).dealSplitCard(any(), eq(false), eq(card2));
    }

    @Test
    @DisplayName("splitHand: Nem engedélyez split-et, ha már túl sok kéz van (max 4)")
    void testSplitHand_TooManyHands() {
        Map<String, PlayerHand> manyHands = new HashMap<>();
        for (int i = 0; i < 4; i++) manyHands.put("h" + i, PlayerHand.builder().build());

        GameState state = GameState.builder()
            .player(PlayerHand.builder().canSplit(true).build())
            .players(manyHands)
            .build();

        GameState result = gameService.splitHand(state);

        assertSame(state, result, "Túl sok kéz esetén az eredeti állapotot kell visszakapni.");
        verifyNoInteractions(gameStateManager);
    }

    @Test
    @DisplayName("splitHand: Nem engedélyez split-et, ha a lapok nem egyforma értékűek (canSplit=false)")
    void testSplitHand_DifferentCards_NoSplit() {
        Card card1 = new Card(Suit.SPADES, Rank.TEN);
        Card card2 = new Card(Suit.HEARTS, Rank.SIX);

        PlayerHand player = PlayerHand.builder()
            .hand(List.of(card1, card2))
            .canSplit(false)
            .build();

        GameState initialState = GameState.builder()
            .player(player)
            .players(new HashMap<>())
            .build();

        GameState result = gameService.splitHand(initialState);

        assertSame(initialState, result, "Eltérő lapok esetén a metódus nem módosíthatja az állapotot.");

        verifyNoInteractions(gameStateManager);
    }

    @Test
    @DisplayName("addToPlayersListByStand: Frissíti a kezet és az indexet, ha van aktív kéz")
    void testAddToPlayersListByStand_Success() {
        String handId = "split-hand-1";
        PlayerHand playerHand = PlayerHand.builder()
            .id(handId)
            .stated(false)
            .build();

        Map<String, PlayerHand> initialPlayers = new HashMap<>();
        initialPlayers.put(handId, playerHand);

        Map<String, Boolean> initialIndex = new HashMap<>();
        initialIndex.put(handId, false);

        GameState oldState = GameState.builder()
            .player(playerHand)
            .players(initialPlayers)
            .playersIndex(initialIndex)
            .build();

        when(gameStateManager.areStatedFalseHands(anyMap())).thenReturn(true);

        GameState result = gameService.addToPlayersListByStand(oldState);

        assertNotNull(result);
        assertNotNull(result.getPlayer());
        assertTrue(result.getPlayer().stated());
        assertTrue(result.getPlayers().get(handId).stated());
        assertTrue(result.getPlayersIndex().get(handId));

        verify(gameStateManager).areStatedFalseHands(anyMap());
    }

    @Test
    @DisplayName("addToPlayersListByStand: Nincs aktív kéz, az állapot változatlan")
    void testAddToPlayersListByStand_Inactive() {
        GameState oldState = GameState.builder().players(new HashMap<>()).build();
        when(gameStateManager.areStatedFalseHands(anyMap())).thenReturn(false);

        GameState result = gameService.addToPlayersListByStand(oldState);

        assertSame(oldState, result, "Nem történt módosítás, az eredeti referenciát kell kapnunk.");
    }

    @Test
    @DisplayName("addToPlayersListByStand: Ha playerHand null, nem történik módosítás")
    void testAddToPlayersListByStand_NullPlayer() {
        GameState state = GameState.builder()
            .player(null) // Szélsőséges eset
            .build();

        when(gameStateManager.areStatedFalseHands(any())).thenReturn(true);

        GameState result = gameService.addToPlayersListByStand(state);

        assertSame(state, result);
    }

    @Test
    @DisplayName("addSplitPlayerToGame: Következő kéz aktiválása a Map-ből (React-biztos logika)")
    void testAddSplitPlayerToGame_FromMapBranch() {
        String currentActiveId = "hand-1";
        String nextWaitingId = "hand-2";

        PlayerHand currentHand = PlayerHand.builder().id(currentActiveId).build();
        PlayerHand nextHand = PlayerHand.builder()
            .id(nextWaitingId)
            .hand(new LinkedList<>(List.of(new Card(Suit.SPADES, Rank.EIGHT))))
            .build();

        Map<String, PlayerHand> playersMap = new HashMap<>();
        playersMap.put(nextWaitingId, nextHand);

        GameState state = GameState.builder()
            .player(currentHand)
            .splitPlayer(null)
            .players(playersMap)
            .playersIndex(Map.of(nextWaitingId, false))
            .build();

        when(gameStateManager.findNextActivePlayerId(any())).thenReturn(Optional.of(nextWaitingId));

        // Dinamikus Mock. Az invocation.getArgument(0) az az állapot,
        // amiből a kód már eltávolította a hand-2-t.
        when(gameStateManager.safeDealCard(any(GameState.class))).thenAnswer(invocation -> {
            GameState currentStateAtCall = invocation.getArgument(0);
            Card dealtCard = new Card(Suit.HEARTS, Rank.TEN);
            return new GameService.DealResultWithState(dealtCard, currentStateAtCall);
        });

        when(handValueCalculator.calculateSum(any())).thenReturn(18);
        when(handStateUpdater.updateHandState(anyInt(), anyInt(), anyBoolean())).thenReturn(HandState.UNDER_21);

        GameState result = gameService.addSplitPlayerToGame(state);

        // Ellenőrizzük, hogy az új ID lett az aktív játékos
        assertNotNull(result.getPlayer());
        assertEquals(nextWaitingId, result.getPlayer().id());

        // Ellenőrizzük a Map-et: Most már false lesz (sikeres törlés),
        // mert a safeDealCard mockja nem írta felül a változtatást a régi state-el.
        assertFalse(result.getPlayers().containsKey(nextWaitingId),
            "A Map-nek üresnek kell lennie, mert a kód eltávolította az ID-t.");

        // Ellenőrizzük, hogy a splitPlayer-be is bekerült mentésként (React-biztonság)
        assertNotNull(result.getSplitPlayer());
        assertEquals(nextWaitingId, result.getSplitPlayer().id());

        // Ellenőrizzük, hogy kapott-e második lapot
        assertEquals(2, result.getPlayer().hand().size());
    }

    @Test
    @DisplayName("addSplitPlayerToGame: Ha nincs több aktív kéz, változatlan állapotot ad vissza")
    void testAddSplitPlayerToGame_NoMoreActivePlayers() {
        GameState state = GameState.builder()
            .playersIndex(Map.of("hand-1", true)) // Mindenki kész (true)
            .build();

        when(gameStateManager.findNextActivePlayerId(any())).thenReturn(Optional.empty());

        GameState result = gameService.addSplitPlayerToGame(state);

        assertSame(state, result, "Üres Optional esetén az eredeti állapotot kell visszakapni.");

        verify(gameStateManager, never()).safeDealCard(any());
        verify(gameStateManager).findNextActivePlayerId(any());
        verifyNoMoreInteractions(handValueCalculator, gameRuleEngine);
    }

    @Test
    @DisplayName("addSplitPlayerToGame: Ha a játékosnak már van 2 lapja, nem kap újat")
    void testAddSplitPlayerToGame_AlreadyHasTwoCards() {
        String handId = "hand-1";
        PlayerHand playerWithTwoCards = PlayerHand.builder()
            .id(handId)
            .hand(List.of(new Card(Suit.SPADES, Rank.ACE), new Card(Suit.HEARTS, Rank.TEN)))
            .build();

        GameState state = GameState.builder()
            .player(playerWithTwoCards)
            .playersIndex(Map.of(handId, false))
            .build();

        when(gameStateManager.findNextActivePlayerId(any())).thenReturn(Optional.of(handId));

        GameState result = gameService.addSplitPlayerToGame(state);

        assertNotNull(result.getPlayer());
        assertEquals(2, result.getPlayer().hand().size(), "Nem szabad harmadik lapot kapnia.");
        verify(gameStateManager, never()).safeDealCard(any()); // Fontos: a safeDealCard-ot nem hívhatja meg!
    }

    @Test
    @DisplayName("addPlayerFromPlayers: Az első várakozó kezet aktívvá teszi és eltávolítja a Map-ből")
    void testAddPlayerFromPlayers_Success() {
        String handId = "split-2";
        PlayerHand handToExtract = PlayerHand.builder().id(handId).build();

        // Fontos a LinkedHashMap a tesztadatban is, hogy modellezzük a sorrendet
        Map<String, PlayerHand> playersMap = new LinkedHashMap<>();
        playersMap.put(handId, handToExtract);
        playersMap.put("other-hand", PlayerHand.builder().id("other").build());

        GameState initialState = GameState.builder()
            .players(playersMap)
            .player(null) // Jelenleg nincs aktív játékos
            .build();

        GameState result = gameService.addPlayerFromPlayers(initialState);

        assertNotNull(result.getPlayer());
        assertEquals(handId, result.getPlayer().id(), "A 'split-2' ID-jú kéznek kell aktívvá válnia.");
        assertEquals(1, result.getPlayers().size(), "Egy kéznek maradnia kell a várólistán.");
        assertFalse(result.getPlayers().containsKey(handId), "Az aktivált kéz nem maradhat a Map-ben.");
    }

    @Test
    @DisplayName("addPlayerFromPlayers: Üres Map esetén nem módosít")
    void testAddPlayerFromPlayers_EmptyMap() {
        GameState state = GameState.builder()
            .players(new HashMap<>())
            .build();

        GameState result = gameService.addPlayerFromPlayers(state);

        assertSame(state, result, "Üres várólista esetén az eredeti referenciát kell kapnunk.");
    }

    @Test
    @DisplayName("addPlayerFromPlayers: A meglévő aktív játékos felülírásra kerül az újjal")
    void testAddPlayerFromPlayers_OverwritesExistingPlayer() {
        PlayerHand activeHand = PlayerHand.builder().id("hand-active").build();
        PlayerHand waitingHand = PlayerHand.builder().id("hand-waiting").build();

        Map<String, PlayerHand> playersMap = new LinkedHashMap<>();
        playersMap.put("hand-waiting", waitingHand);

        GameState initialState = GameState.builder()
            .player(activeHand) // Van egy aktív játékos
            .players(playersMap)
            .build();

        GameState result = gameService.addPlayerFromPlayers(initialState);

        assertNotNull(result.getPlayer());
        assertEquals("hand-waiting", result.getPlayer().id(), "A várakozó kéznek kell átvennie a helyet.");
        assertNotEquals("hand-active", result.getPlayer().id(), "A régi aktív kéznek el kell tűnnie az aktív helyről.");
    }

    @Test
    @DisplayName("restartGame: Kényszerített alaphelyzet és pakli újratöltés")
    void testRestartGame_EmergencyRecoveryWithDeck() {
        List<Card> partialCards = Collections.nCopies(84, new Card(Suit.SPADES, Rank.ACE));

        GameState glitchedState = GameState.builder()
            .isRoundActive(true)
            .wasSplitInRound(true)
            .bet(100)
            .natural21(2)
            .winner(1)
            .deck(new Deck(partialCards)) // Teljesen üres pakli a hibaállapotban
            .build();

        GameState cleanedInternalState = GameState.builder()
            .player(PlayerHand.createEmptyHand())
            .dealerMasked(DealerHandMasked.createEmptyHand())
            .dealerUnmasked(DealerHandUnmasked.createEmptyHand())
            .players(new HashMap<>())
            .deck(new Deck(partialCards))
            .build();

        when(gameStateManager.resetRoundState(glitchedState)).thenReturn(cleanedInternalState);

        GameState result = gameService.restartGame(glitchedState);

        assertAll("A helyreállítási folyamatnak minden mezőt és a paklit is nulláznia kell",
            () -> assertEquals(0, result.getBet(), "A tét nem maradhat meg."),
            () -> assertFalse(result.isRoundActive(), "A körnek lezártnak kell lennie."),
            () -> assertFalse(result.isWasSplitInRound(), "A split flaget törölni kell."),
            () -> assertEquals(0, result.getNatural21(), "A BlackJack jelzőt nullázni kell."),
            () -> assertEquals(0, result.getWinner(), "A győztest törölni kell."),
            // Pakli ellenőrzése
            () -> assertNotEquals(84, result.getDeckLen(), "A régi 84 lapos paklit le kellene cserélni."),
            () -> assertEquals(INITIAL_DECK_LENGTH, result.getDeckLen(), "A paklinak teljesnek kell lennie (6 pakli * 52 lap).")
        );

        verify(gameStateManager, times(1)).resetRoundState(glitchedState);
    }

    @Test
    @DisplayName("Integrációs teszt: A restartGame által létrehozott 12 lapos pakli megmarad az osztás végéig")
    void testDeckPersistsThroughFlow() {
        // 1. Arrange - 12 lapos speciális pakli létrehozása
        Card c_01 = new Card(Suit.HEARTS, Rank.ACE);   // Player 1
        Card c_02 = new Card(Suit.CLUBS, Rank.TWO);    // Dealer 1 (rejtett)
        Card c_03 = new Card(Suit.DIAMONDS, Rank.THREE);// Player 2
        Card c_04 = new Card(Suit.SPADES, Rank.FOUR);  // Dealer 2 (látható)

        // 12 lapos pakli
        List<Card> twelveCards = new ArrayList<>(List.of(
            c_01, c_02, c_03, c_04,
            new Card(Suit.HEARTS, Rank.FIVE), new Card(Suit.HEARTS, Rank.SIX),
            new Card(Suit.HEARTS, Rank.SEVEN), new Card(Suit.HEARTS, Rank.EIGHT),
            new Card(Suit.HEARTS, Rank.NINE), new Card(Suit.HEARTS, Rank.TEN),
            new Card(Suit.HEARTS, Rank.JACK), new Card(Suit.DIAMONDS, Rank.QUEEN)
        ));
        Deck twelveCardDeck = new Deck(twelveCards);

        // Mockoljuk a resetRoundState-et, hogy a VALÓDI kód fusson le (toBuilder tesztelése)
        when(gameStateManager.resetRoundState(any())).thenCallRealMethod();

        // Dinamikus osztás: kiszedi a soron következő lapot a kapott state paklijából
        when(gameStateManager.safeDealCard(any(GameState.class))).thenAnswer(invocation -> {
            GameState s = invocation.getArgument(0);

            // Ellenőrizzük, hogy van-e pakli a kapott állapotban
            if (s.getDeck() == null || s.getDeck().deck().isEmpty()) {
                throw new IllegalStateException("A pakli üres vagy null az osztásnál!");
            }

            Card dealt = s.getDeck().deck().getFirst();
            List<Card> remaining = new ArrayList<>(s.getDeck().deck());
            remaining.removeFirst();

            // Visszaadjuk a lapot és az ÚJ állapotot a CSÖKKENTETT paklival
            return new GameService.DealResultWithState(dealt, s.toBuilder()
                .deck(new Deck(remaining))
                .build());
        });

        // Szabálymotor mockolása (hogy az initializeNewRound sikeresen befejeződjön)
        when(handValueCalculator.calculateSum(anyList())).thenReturn(14); // Pl. ACE + 3
        when(handStateUpdater.updateHandState(anyInt(), anyInt(), anyBoolean())).thenReturn(HandState.UNDER_21);
        when(gameRuleEngine.initNatural21State(any())).thenReturn(WinnerState.NONE.getValue());

        // 2. Act
        // Meghívjuk a restartot (ez belsőleg a valódi resetRoundState-et hívja a mock-on keresztül)
        GameState stateAfterRestart = gameService.restartGame(GameState.builder().build());

        // Beoltjuk a 12 lapos paklival (szimulálva, hogy a restartGame ezt generálta)
        GameState stateWithControlledDeck = stateAfterRestart.toBuilder()
            .deck(twelveCardDeck)
            .build();

        // Elindítjuk az osztást (ez ismét hívja a resetRoundState-et, majd 4-szer a safeDealCard-ot)
        GameState finalState = gameService.initializeNewRound(stateWithControlledDeck);

        // 3. Assert
        assertNotNull(finalState.getPlayer(), "A játékos keze nem lehet null.");

        // Ellenőrizzük, hogy a játékos első lapja tényleg az Ász (c_01)
        assertEquals(c_01, finalState.getPlayer().hand().getFirst(),
            "A játékos első lapjának az ACE-nek kell lennie a 12-es pakliból.");

        // Ellenőrizzük a Dealer látható lapját (c_04)
        assertNotNull(finalState.getDealerMasked());
        assertEquals(c_04, finalState.getDealerMasked().hand().get(1),
            "A dealer látható lapjának a c_04-nek kell lennie.");

        // Ellenőrizzük a pakli fogyását: 12 lapból 4-et osztottunk ki
        assertNotNull(finalState.getDeck());
        assertEquals(8, finalState.getDeck().deck().size(),
            "A pakliban pontosan 8 lapnak kell maradnia 4 osztás után.");

        // 3. Assert (Kibővítve a maradék lapok ellenőrzésével)
        assertNotNull(finalState.getDeck());
        assertEquals(8, finalState.getDeck().deck().size(),
            "A pakliban pontosan 8 lapnak kell maradnia 4 osztás után.");

        // Ellenőrizzük, hogy a pakli tetején (a 0. indexen) a soron következő lap (c_05) van-e
        // A 12 lapból az első 4 elment (c_01, c_02, c_03, c_04), tehát a 5. lap következik
        Card expectedNextCard = new Card(Suit.HEARTS, Rank.FIVE);
        assertEquals(expectedNextCard, finalState.getDeck().deck().getFirst(),
            "A pakli következő lapjának a Kőr 5-ösnek kell lennie.");

        // Ha a teljes maradék listát ellenőrizni akarod sorrendben:
        List<Card> remainingCards = finalState.getDeck().deck();
        assertEquals(Rank.FIVE,  remainingCards.get(0).rank());
        assertEquals(Rank.SIX,   remainingCards.get(1).rank());
        assertEquals(Rank.SEVEN, remainingCards.get(2).rank());
        assertEquals(Rank.EIGHT, remainingCards.get(3).rank());
        assertEquals(Rank.NINE,  remainingCards.get(4).rank());
        assertEquals(Rank.TEN,   remainingCards.get(5).rank());
        assertEquals(Rank.JACK,  remainingCards.get(6).rank());
        assertEquals(Rank.QUEEN, remainingCards.get(7).rank());

        // 1. Ellenőrizzük a JÁTÉKOS szemszögéből (Masked Hand)
        // Az első lap maszkolva van: rangja "✪", színe üres " "
        Card maskedHoleCard = finalState.getDealerMasked().hand().getFirst();
        assertEquals("✪", maskedHoleCard.rank().getSymbol(), "A dealer első lapja maszkolt kell legyen!");
        assertEquals(" ", maskedHoleCard.suit().getSymbol(), "A maszkolt lap színe üres karakter!");

        // A második lap viszont látható kell legyen a játékosnak
        assertEquals(c_04, finalState.getDealerMasked().hand().get(1), "A dealer második lapja viszont látható!");

        // 2. Ellenőrizzük a SZERVER szemszögéből (Unmasked Hand)
        // Itt derül ki, hogy a safeDealCard tényleg a c_02-t (Treff 2-est) osztotta-e ki rejtve
        assertNotNull(finalState.getDealerUnmasked(), "A belső unmasked kéz nem lehet null!");
        assertEquals(c_02, finalState.getDealerUnmasked().hand().getFirst(),
            "A szervernek tudnia kell, hogy a rejtett lap valójában a c_02 volt!");

        // 3. Ellenőrizzük a pakli integritását
        assertNotNull(finalState.getDeck());
        assertEquals(8, finalState.getDeck().deck().size(), "8 lap maradt a pakliban.");
        assertEquals(new Card(Suit.HEARTS, Rank.FIVE), finalState.getDeck().deck().getFirst(),
            "A következő kártya a pakli tetején a Kőr 5-ös.");
    }
}
