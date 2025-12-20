package com.blackjack.blackjack.service;

import com.blackjack.blackjack.dto.bet.GameStateBet;
import com.blackjack.blackjack.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GameServiceTest {

    @Mock
    private GameStateService gameStateService;
    @Mock
    private GameRuleEngine gameRuleEngine;
    @Mock
    private GameStateManager gameStateManager;
    @Mock
    private DeckService deckService;
    @Mock
    private BetService betService;
    @Mock
    private HandValueCalculator handValueCalculator;
    @Mock
    private HandStateUpdater handStateUpdater;

    @InjectMocks
    private GameService gameService;

    private GameState createBaseState(int bet) {
        return GameState.builder()
            .bet(bet)
            .betList(new ArrayList<>(List.of(bet)))
            .player(PlayerHand.createEmptyHand().toBuilder().bet(bet).build())
            .dealerUnmasked(DealerHandUnmasked.createEmptyHand())
            .dealerMasked(DealerHandMasked.createEmptyHand())
            .deck(Deck.createNewDeck())
            .isRoundActive(true)
            .players(new HashMap<>())
            .build();
    }

    @Test
    void testInsuranceRequest_DealerHasNoBlackjack() {
        GameState state = createBaseState(100); // 100-as tét

        DealerHandUnmasked dealer = DealerHandUnmasked.createEmptyHand().toBuilder()
            .natural21(0) // Nincs Blackjack
            .build();
        state = state.toBuilder().dealerUnmasked(dealer).build();

        GameService.TransactionResult result = gameService.insuranceRequest(state);

        assertEquals(-50, result.transactionResult());
        assertTrue(result.gameState().isRoundActive());
    }

    @Test
    void testInsuranceRequest_DealerHasBlackjack() {
        GameState state = createBaseState(100);

        DealerHandUnmasked dealer = DealerHandUnmasked.createEmptyHand().toBuilder()
            .natural21(WinnerState.BLACKJACK_DEALER_WON.getValue())
            .build();

        state = state.toBuilder()
            .dealerUnmasked(dealer)
            .isRoundActive(true)
            .build();

        GameService.TransactionResult result = gameService.insuranceRequest(state);

        assertEquals(100, result.transactionResult(), "Dealer Blackjack esetén a biztosítás az eredeti tét összegét adja vissza");
        assertFalse(result.gameState().isRoundActive(), "Dealer Blackjack esetén a biztosítás után a körnek le kell zárulnia");
    }

    @Test
    void testApplyDoubleBet_ShouldDoubleCorrectly() {
        GameState state = createBaseState(100);

        GameState result = gameService.applyDoubleBet(state, 100);

        assertEquals(200, result.getBet());
        assertNotNull(result.getPlayer());
        assertEquals(200, result.getPlayer().bet());
    }

    @Test
    void testPlaceBet_ShouldAccumulateBetsAndHistory() {
        // 1. Kezdőállapot: deck null, minden üres
        GameState initialState = createBaseState(0).toBuilder()
            .bet(0)
            .betList(new ArrayList<>())
            .deck(null)
            .build();

        GameStateBet dto1 = betService.placeBet(initialState, 50);

        assertEquals(50, dto1.bet());
        assertEquals(List.of(50), dto1.betList());
        assertEquals(104, dto1.deckLen()); // A "virtuális" 104-es érték ellenőrzése

        GameState intermediateState = initialState.toBuilder()
            .bet(dto1.bet())
            .betList(dto1.betList())
            .build();

        GameStateBet dto2 = betService.placeBet(intermediateState, 100);

        assertEquals(150, dto2.bet());
        assertEquals(List.of(50, 100), dto2.betList());
        assertEquals(104, dto2.deckLen());
    }

    @Test
    void testRetakeBet_ShouldRemoveLastChipAndHandleStateTransition() {
        GameState initialState = createBaseState(150).toBuilder()
            .bet(150)
            .betList(new ArrayList<>(List.of(50, 100)))
            .isRoundActive(true)
            .deck(null)
            .build();

        BetService.RetakeResult result1 = betService.retakeBet(initialState);
        GameStateBet dto1 = result1.newState();

        assertEquals(100, result1.amountReturned());
        assertEquals(50, dto1.bet());
        assertEquals(List.of(50), dto1.betList());
        assertEquals(104, dto1.deckLen()); // Itt is elvárjuk a 104-et!

        GameState intermediateState = initialState.toBuilder()
            .bet(dto1.bet())
            .betList(dto1.betList())
            .isRoundActive(!dto1.betList().isEmpty())
            .build();

        assertTrue(intermediateState.isRoundActive());

        BetService.RetakeResult result2 = betService.retakeBet(intermediateState);
        GameStateBet dto2 = result2.newState();

        GameState finalState = intermediateState.toBuilder()
            .bet(dto2.bet())
            .betList(dto2.betList())
            .isRoundActive(!dto2.betList().isEmpty())
            .build();

        assertEquals(50, result2.amountReturned());
        assertEquals(0, dto2.bet());
        assertTrue(dto2.betList().isEmpty());
        assertFalse(finalState.isRoundActive()); // Végre kikapcsolt
    }

    @Test
    void testCreateNewDeck_ShouldUpdateGameStateWithNewDeck() {
        GameState initialState = createBaseState(100);

        Deck expectedDeck = Deck.createNewDeck();

        when(deckService.createNewDeck()).thenReturn(expectedDeck);

        GameState resultState = gameStateManager.createNewDeck(initialState);

        verify(deckService, times(1)).createNewDeck();

        assertEquals(expectedDeck, resultState.getDeck());
        assertEquals(100, resultState.getBet());
    }

    @Test
    void testSafeDealCard_NormalCase_ShouldReturnCardAndNewState() {
        GameState state = createBaseState(100);
        Card expectedCard = new Card(Suit.HEARTS, Rank.ACE);
        Deck remainingDeck = Deck.builder().deck(List.of()).build(); // Üres maradék pakli szimulálása
        DealResult dealResult = new DealResult(expectedCard, remainingDeck);

        when(deckService.dealCard(any(Deck.class))).thenReturn(dealResult);

        GameService.DealResultWithState result = gameStateManager.safeDealCard(state);

        assertEquals(expectedCard, result.dealtCard());
        assertEquals(remainingDeck, result.newGameState().getDeck());
        verify(deckService, times(1)).dealCard(any());
    }

    @Test
    void testSafeDealCard_DeckExhausted_ShouldReshuffleAndTryAgain() {
        GameState state = createBaseState(100);
        Deck newDeck = Deck.createNewDeck();
        Card expectedCard = new Card(Suit.SPADES, Rank.KING);
        DealResult successfulResult = new DealResult(expectedCard, newDeck);

        // 1. Első hívásra hibát dob (üres pakli szimulálása)
        // 2. Második hívásra (az újrakeverés után) sikeresen ad egy lapot
        when(deckService.dealCard(any()))
            .thenThrow(new IllegalStateException("Empty")) // Első próbálkozás elbukik
            .thenReturn(successfulResult);                // Második (reshuffle utáni) sikerül

        when(deckService.createNewDeck()).thenReturn(newDeck);

        GameService.DealResultWithState result = gameStateManager.safeDealCard(state);

        assertEquals(expectedCard, result.dealtCard());
        verify(deckService, times(1)).createNewDeck(); // Ellenőrizzük, hogy volt-e újrakeverés
        verify(deckService, times(2)).dealCard(any()); // Összesen kétszer kellett hívni az osztást
    }

    @Test
    void testInitializeNewRound_ShouldSetupEverythingCorrectly() {
        GameState baseState = createBaseState(100);
        Card pCard1 = new Card(Suit.HEARTS, Rank.TEN);
        Card dCard1 = new Card(Suit.CLUBS, Rank.FIVE);
        Card pCard2 = new Card(Suit.DIAMONDS, Rank.SIX);
        Card dCard2 = new Card(Suit.SPADES, Rank.ACE); // Ász az osztónak -> Insurance kell!

        when(deckService.dealCard(any()))
            .thenReturn(new DealResult(pCard1, baseState.getDeck()))
            .thenReturn(new DealResult(dCard1, baseState.getDeck()))
            .thenReturn(new DealResult(pCard2, baseState.getDeck()))
            .thenReturn(new DealResult(dCard2, baseState.getDeck()));

        when(handValueCalculator.calculateSum(any())).thenReturn(16); // Player 10+6
        when(handStateUpdater.updateHandState(anyInt(), anyInt())).thenReturn(HandState.UNDER_21);
        when(gameRuleEngine.canSplit(any())).thenReturn(false);
        when(gameRuleEngine.initNatural21State(any())).thenReturn(0); // WinnerState.NONE

        GameState result = gameService.initializeNewRound(baseState);

        // Játékos adatai
        assertNotNull(result.getPlayer());
        assertEquals(2, result.getPlayer().hand().size());
        assertEquals(16, result.getPlayer().sum());
        assertEquals("P-001", result.getPlayer().id());
        assertNotNull(result.getDealerMasked());
        assertEquals(2, result.getDealerMasked().hand().size());

        Card expectedMasked = Card.createMaskedCard();
        assertEquals(expectedMasked, result.getDealerMasked().hand().get(0),
            "Az osztó első lapjának maszkoltnak kell lennie (✪ szimbólum)");
        assertEquals(Rank.ACE, result.getDealerMasked().hand().get(1).rank());

        // Osztó adatai (MaskedHand)
        assertEquals(2, result.getDealerMasked().hand().size());
        assertEquals(Rank.ACE, result.getDealerMasked().hand().get(1).rank());
        assertTrue(result.getDealerMasked().canInsure(), "Ásznál fel kell ajánlani a biztosítást");

        // Állapot adatok
        assertTrue(result.isRoundActive());
        assertEquals(1, result.getHandCounter());

        // Szerviz hívások ellenőrzése
        verify(deckService, times(4)).dealCard(any());
    }

    @Test
    void testInitializeNewRound_ShouldSetAcesTrue_WhenPlayerHasTwoAces() {
        GameState baseState = createBaseState(100);

        Card pCard1 = new Card(Suit.HEARTS, Rank.ACE);
        Card dCard1 = new Card(Suit.CLUBS, Rank.FIVE);
        Card pCard2 = new Card(Suit.DIAMONDS, Rank.ACE);
        Card dCard2 = new Card(Suit.SPADES, Rank.TEN);

        // Kártyaosztás sorrendje: P1, D1, P2, D2
        when(deckService.dealCard(any()))
            .thenReturn(new DealResult(pCard1, baseState.getDeck()))
            .thenReturn(new DealResult(dCard1, baseState.getDeck()))
            .thenReturn(new DealResult(pCard2, baseState.getDeck()))
            .thenReturn(new DealResult(dCard2, baseState.getDeck()));

        // Ászok összege 2 vagy 12 (Blackjack függő), de most a kalkulátor 12-t ad
        when(handValueCalculator.calculateSum(any())).thenReturn(12);
        when(handStateUpdater.updateHandState(anyInt(), anyInt())).thenReturn(HandState.UNDER_21);

        // Két Ász esetén a Split
        when(gameRuleEngine.canSplit(any())).thenReturn(true);
        when(gameRuleEngine.initNatural21State(any())).thenReturn(0);

        GameState result = gameService.initializeNewRound(baseState);

        assertTrue(result.isAces(), "Ha az első és a harmadik lap Ász, az 'aces' mezőnek true-nak kell lennie!");

        assertEquals(Rank.ACE, result.getPlayer().hand().get(0).rank());
        assertEquals(Rank.ACE, result.getPlayer().hand().get(1).rank());
        assertEquals(12, result.getPlayer().sum());
    }

    @Test
    void testPlayerHit_ShouldAddCardAndUpdateSum() {
        Card initialCard = new Card(Suit.HEARTS, Rank.FIVE);
        Card drawnCard = new Card(Suit.DIAMONDS, Rank.TEN);

        GameState state = createBaseState(100).toBuilder()
            .player(PlayerHand.createEmptyHand().toBuilder()
                .hand(new LinkedList<>(List.of(initialCard)))
                .sum(5)
                .build())
            .build();

        // Megjegyzés: Mivel a playerHit a safeDealCard-ot hívja, ami a deckService-t,
        // a mock-ot a deckService-re állítjuk be.
        when(deckService.dealCard(any())).thenReturn(new DealResult(drawnCard, state.getDeck()));

        when(handValueCalculator.calculateSum(any())).thenReturn(15);
        when(handStateUpdater.updateHandState(15, 2)).thenReturn(HandState.UNDER_21);

        GameState result = gameService.playerHit(state);

        assertEquals(2, result.getPlayer().hand().size());
        assertEquals(15, result.getPlayer().sum());
        assertFalse(result.getPlayer().stated(), "15 pontnál még nem kellene megállnia automatikusan");
        assertEquals(drawnCard, result.getPlayer().hand().get(1));
    }

    @Test
    void testPlayerHit_ShouldSetStatedTrue_WhenBust() {
        GameState state = createBaseState(100); // Alaphelyzet
        Card bustCard = new Card(Suit.CLUBS, Rank.TEN);

        when(deckService.dealCard(any())).thenReturn(new DealResult(bustCard, state.getDeck()));

        // Szimuláljuk, hogy a számítás 25-öt ad (Bust)
        when(handValueCalculator.calculateSum(any())).thenReturn(25);
        when(handStateUpdater.updateHandState(eq(25), anyInt())).thenReturn(HandState.BUST);

        GameState result = gameService.playerHit(state);

        assertEquals(25, result.getPlayer().sum());
        assertEquals(HandState.BUST.getValue(), result.getPlayer().handState());
        assertTrue(result.getPlayer().stated(), "Bust esetén a stated-nek true-nak kell lennie");
    }

    @Test
    void testPlayerStand_ShouldCompleteDealerTurnAndSetWinner() {
        Card dCard1 = new Card(Suit.HEARTS, Rank.FIVE);
        Card dCard2 = new Card(Suit.DIAMONDS, Rank.EIGHT);
        Card dCard3 = new Card(Suit.CLUBS, Rank.SIX); // Ezt fogja a Dealer húzni

        // Játékos megáll 18-nál (2 lapja van)
        PlayerHand player = PlayerHand.createEmptyHand().toBuilder()
            .hand(new LinkedList<>(List.of(new Card(Suit.SPADES, Rank.TEN), new Card(Suit.SPADES, Rank.EIGHT))))
            .sum(18)
            .stated(false)
            .build();

        // Osztónak 13 pontja van (5 + 8)
        DealerHandUnmasked dealer = DealerHandUnmasked.createEmptyHand().toBuilder()
            .hand(new LinkedList<>(List.of(dCard1, dCard2)))
            .sum(13)
            .build();

        GameState state = createBaseState(100).toBuilder()
            .player(player)
            .dealerUnmasked(dealer)
            .isRoundActive(true)
            .build();

        // 2. MOCKOK BEÁLLÍTÁSA
        // Biztonsági mentés: minden egyéb calculateSum hívásra adjon 0-t, ne null-t
        when(handValueCalculator.calculateSum(any())).thenReturn(0);

        // Akkor adjon 13-at, ha a Dealer kezdő keze (2 lap) van benne
        when(handValueCalculator.calculateSum(argThat(l -> l != null && l.size() == 2)))
            .thenReturn(13);

        // Akkor adjon 19-et, ha már a húzott kártyával bővült kéz (3 lap) van benne
        when(handValueCalculator.calculateSum(argThat(l -> l != null && l.size() == 3)))
            .thenReturn(19);

        // Kártyaosztás szimulálása: a Dealer kap egy 6-ost (dCard3)
        when(deckService.dealCard(any())).thenReturn(new DealResult(dCard3, state.getDeck()));

        // HandState mockolása (Kétszer hívódik: Stand-nél és a Dealer húzásnál)
        when(handStateUpdater.updateHandState(anyInt(), anyInt())).thenReturn(HandState.UNDER_21);

        // WinnerState: A Dealer nyert (19 > 18)
        when(gameRuleEngine.winnerStateUpdater(any(), any())).thenReturn(WinnerState.DEALER_WON.getValue());

        // 3. WHEN
        GameState finalState = gameService.playerStand(state);

        // 4. THEN - Ellenőrzések
        // Játékos állapota
        assertTrue(finalState.getPlayer().stated(), "A játékosnak stated=true állapotba kell kerülnie");

        // Dealer állapota
        assertEquals(3, finalState.getDealerUnmasked().hand().size(), "A Dealernek húznia kellett egy 3. lapot");
        assertEquals(19, finalState.getDealerUnmasked().sum(), "A Dealer végpontszáma 19 kell legyen");
        assertEquals(dCard3, finalState.getDealerUnmasked().hand().getLast(), "Az utolsó lapnak a húzott 6-osnak kell lennie");

        // Kör állapota
        assertFalse(finalState.isRoundActive(), "A körnek le kell zárulnia");
        assertEquals(WinnerState.DEALER_WON.getValue(), finalState.getWinner(), "A győztes a Dealer kell legyen");

        // Ellenőrizzük, hogy tényleg meghívódott-e az osztás
        verify(deckService, times(1)).dealCard(any());
    }

    @Test
    void testPlayerStand_DealerBusts_PlayerWins() {
        Card d1 = new Card(Suit.HEARTS, Rank.FIVE);
        Card d2 = new Card(Suit.CLUBS, Rank.TEN);
        Card bustCard = new Card(Suit.SPADES, Rank.TEN);

        GameState state = createBaseState(100).toBuilder()
            .player(PlayerHand.createEmptyHand().toBuilder()
                .hand(new LinkedList<>(List.of(new Card(Suit.DIAMONDS, Rank.KING), new Card(Suit.DIAMONDS, Rank.EIGHT))))
                .sum(18).build())
            .dealerUnmasked(DealerHandUnmasked.createEmptyHand().toBuilder()
                .hand(new LinkedList<>(List.of(d1, d2))).sum(15).build())
            .isRoundActive(true)
            .build();

        when(deckService.dealCard(any())).thenReturn(new DealResult(bustCard, state.getDeck()));

        // Számítások: 2 lap = 15, 3 lap = 25
        when(handValueCalculator.calculateSum(argThat(l -> l != null && l.size() == 2))).thenReturn(15);
        when(handValueCalculator.calculateSum(argThat(l -> l != null && l.size() == 3))).thenReturn(25);

        when(handStateUpdater.updateHandState(anyInt(), anyInt())).thenAnswer(invocation -> {
            int sum = invocation.getArgument(0);
            return sum > 21 ? HandState.BUST : HandState.UNDER_21;
        });

        when(gameRuleEngine.winnerStateUpdater(any(), any())).thenReturn(WinnerState.PLAYER_WON.getValue());

        GameState finalState = gameService.playerStand(state);

        assertEquals(HandState.BUST.getValue(), finalState.getDealerUnmasked().handState());
        assertEquals(WinnerState.PLAYER_WON.getValue(), finalState.getWinner());
        assertFalse(finalState.isRoundActive());
        verify(deckService, times(1)).dealCard(any());
    }

    @Test
    void testPlayerStand_DealerAlreadyAt17_NoDrawing() {
        // Dealernek már megvan a 17-je (Hetes + Tízes)
        GameState state = createBaseState(100).toBuilder()
            .player(PlayerHand.createEmptyHand().toBuilder()
                .hand(new LinkedList<>(List.of(new Card(Suit.SPADES, Rank.KING), new Card(Suit.SPADES, Rank.EIGHT))))
                .sum(18).build())
            .dealerUnmasked(DealerHandUnmasked.createEmptyHand().toBuilder()
                .hand(new LinkedList<>(List.of(new Card(Suit.HEARTS, Rank.SEVEN), new Card(Suit.CLUBS, Rank.TEN))))
                .sum(17).build())
            .isRoundActive(true)
            .build();

        // A kalkulátor 17-et ad a Dealer 2 lapjára
        when(handValueCalculator.calculateSum(argThat(l -> l != null && l.size() == 2))).thenReturn(17);

        // Rugalmas HandState mockolás (lekezeli a játékos 18-át és a dealer 17-ét is)
        when(handStateUpdater.updateHandState(anyInt(), anyInt())).thenReturn(HandState.UNDER_21);

        // RuleEngine szerint a játékos nyert (18 > 17)
        when(gameRuleEngine.winnerStateUpdater(any(), any())).thenReturn(WinnerState.PLAYER_WON.getValue());

        GameState finalState = gameService.playerStand(state);

        assertEquals(2, finalState.getDealerUnmasked().hand().size(), "A Dealer nem húzhatott volna lapot, mert 17-en áll");
        assertEquals(17, finalState.getDealerUnmasked().sum());

        // A legfontosabb ellenőrzés: SOSEM hívódott meg a kártyaosztás
        verify(deckService, never()).dealCard(any());
        assertFalse(finalState.isRoundActive());
    }

    @Test
    void testCalculateRewards_PlayerHasBlackjack_ShouldReturnTwoAndHalfTimesBet() {
        PlayerHand player = PlayerHand.createEmptyHand().toBuilder()
            .bet(100)
            .build();

        GameState state = createBaseState(100).toBuilder()
            .player(player)
            .natural21(WinnerState.BLACKJACK_PLAYER_WON.getValue())
            .build();

        GameService.TransactionResult result = gameService.calculateRewards(state);

        assertEquals(250, result.transactionResult(), "100-as tétnél 250 jár vissza Blackjackre");
        assertEquals(0, result.gameState().getPlayer().bet(), "A tétet le kell nullázni kifizetés után");
        assertEquals(0, result.gameState().getBetList().size(), "A betList-et ki kell üríteni");
        verify(gameStateService).saveGameState(any()); // Fontos, hogy mentse az állapotot
    }

    @Test
    void testCalculateRewards_PlayerWins_ShouldDoubleTheBet() {
        PlayerHand player = PlayerHand.createEmptyHand().toBuilder().bet(100).build();
        DealerHandUnmasked dealer = DealerHandUnmasked.createEmptyHand();

        GameState state = createBaseState(100).toBuilder()
            .player(player)
            .dealerUnmasked(dealer)
            .winner(WinnerState.PLAYER_WON.getValue())
            .natural21(0)
            .build();

        GameService.TransactionResult result = gameService.calculateRewards(state);

        assertEquals(200, result.transactionResult(), "Sima győzelemnél a tét duplája jár");
    }

    @Test
    void testCalculateRewards_Push_ShouldReturnOriginalBet() {
        PlayerHand player = PlayerHand.createEmptyHand().toBuilder().bet(100).build();
        DealerHandUnmasked dealer = DealerHandUnmasked.createEmptyHand();

        GameState state = createBaseState(100).toBuilder()
            .player(player)
            .dealerUnmasked(dealer)
            .winner(WinnerState.PUSH.getValue())
            .build();

        GameService.TransactionResult result = gameService.calculateRewards(state);

        assertEquals(100, result.transactionResult(), "Döntetlennél a tét visszajár");
    }

    @Test
    void testCalculateRewards_DealerHasBlackjack_PlayerLoses() {
        PlayerHand player = PlayerHand.createEmptyHand().toBuilder()
            .bet(100)
            .sum(20)
            .build();

        DealerHandUnmasked dealer = DealerHandUnmasked.createEmptyHand().toBuilder()
            .natural21(WinnerState.BLACKJACK_DEALER_WON.getValue())
            .build();

        GameState state = createBaseState(100).toBuilder()
            .player(player)
            .dealerUnmasked(dealer)
            .winner(WinnerState.DEALER_WON.getValue()) // A RuleEngine szerint a Dealer nyert
            .build();

        GameService.TransactionResult result = gameService.calculateRewards(state);

        assertEquals(0, result.transactionResult(), "Dealer Blackjack esetén a játékos 0 zsetont kap vissza");
        assertEquals(0, result.gameState().getPlayer().bet(), "A tétet mindenképpen le kell nullázni");
    }

    @Test
    void testCalculateRewards_BothHaveBlackjack_ShouldReturnPush() {
        PlayerHand player = PlayerHand.createEmptyHand().toBuilder()
            .bet(100)
            .sum(21)
            .build();

        DealerHandUnmasked dealer = DealerHandUnmasked.createEmptyHand().toBuilder()
            .natural21(WinnerState.BLACKJACK_PUSH.getValue())
            .sum(21)
            .build();

        GameState state = createBaseState(100).toBuilder()
            .player(player)
            .dealerUnmasked(dealer)
            // Itt a WinnerState is PUSH-on kell legyen a RuleEngine szerint
            .winner(WinnerState.PUSH.getValue())
            .build();

        GameService.TransactionResult result = gameService.calculateRewards(state);

        assertEquals(100, result.transactionResult(),
            "Ha mindkettőnek Blackjackje van, a tétnek (100) vissza kell járnia.");
        assertEquals(0, result.gameState().getPlayer().bet(),
            "A kifizetés után a tétet le kell nullázni az állapotban.");

        verify(gameStateService).saveGameState(any());
    }
}
