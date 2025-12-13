package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class GameRuleEngineTest {
    private final Card testCardAce = new Card(Suit.HEARTS, Rank.ACE);
    private final Card testCardTen = new Card(Suit.HEARTS, Rank.TEN);
    private final Card mockDrawnCard = mock(Card.class);
    @InjectMocks
    private GameRuleEngine gameRuleEngine;
    @Mock
    private GameState mockGameState;
    @Mock
    private PlayerHand mockPlayerHand;
    @Mock
    private DealerHandUnmasked mockDealerHand;

    private void setupMocks(int playerSize, int playerSum, int dealerSize, int dealerSum) {
        @SuppressWarnings("unchecked")
        LinkedList<Card> mockPlayerHandList = mock(LinkedList.class);

        @SuppressWarnings("unchecked")
        List<Card> mockDealerHandList = mock(List.class);

        when(mockPlayerHand.getHand()).thenReturn(mockPlayerHandList);
        when(mockPlayerHandList.size()).thenReturn(playerSize);
        when(mockPlayerHand.getSum()).thenReturn(playerSum);

        when(mockDealerHand.getHand()).thenReturn(mockDealerHandList);
        when(mockDealerHandList.size()).thenReturn(dealerSize);
        when(mockDealerHand.getSum()).thenReturn(dealerSum);

        when(mockGameState.getPlayer()).thenReturn(mockPlayerHand);
        when(mockGameState.getDealerUnmasked()).thenReturn(mockDealerHand);
    }

    private Card mockTenValueCard(Rank rank) {
        Rank mockRank = mock(Rank.class);

        when(mockRank.getBaseValue()).thenReturn(10);
        when(mockRank.name()).thenReturn(rank.name());

        Card card = mock(Card.class);

        when(card.rank()).thenReturn(mockRank);

        return card;
    }

    @Test
    void initNatural21State_shouldReturnPush_whenBothHaveNatural21() {
        setupMocks(2, 21, 2, 21);

        int result = gameRuleEngine.initNatural21State(mockGameState);

        assertEquals(2, result);
    }

    @Test
    void initNatural21State_shouldReturnPlayerWon_whenOnlyPlayerHasNatural21() {
        setupMocks(2, 21, 2, 18);

        int result = gameRuleEngine.initNatural21State(mockGameState);

        assertEquals(1, result, "Csak a Játékos Natural 21 esetén PLAYER_WON (1) az elvárt.");
    }

    @Test
    void initNatural21State_shouldReturnDealerWon_whenOnlyDealerHasNatural21() {
        setupMocks(2, 19, 2, 21);

        int result = gameRuleEngine.initNatural21State(mockGameState);

        assertEquals(3, result, "Csak a Dealer Natural 21 esetén DEALER_WON (3) az elvárt.");
    }

    @Test
    void initNatural21State_shouldReturnNone_whenNeitherHasNatural21() {
        setupMocks(3, 21, 3, 21);
        assertEquals(0, gameRuleEngine.initNatural21State(mockGameState));

        setupMocks(2, 20, 2, 20);
        assertEquals(0, gameRuleEngine.initNatural21State(mockGameState));
    }

    @Test
    void winnerStateUpdater_shouldReturnPlayerLost_whenPlayerBusts() {
        setupMocks(5, 22, 2, 18);

        int result = gameRuleEngine.winnerStateUpdater(mockGameState);

        assertEquals(5, result);
    }

    @Test
    void winnerStateUpdater_shouldReturnPlayerWon_whenDealerBusts() {
        setupMocks(2, 20, 4, 22);

        int result = gameRuleEngine.winnerStateUpdater(mockGameState);

        assertEquals(6, result);
    }

    @Test
    void winnerStateUpdater_shouldReturnPush_whenPush() {
        setupMocks(3, 20, 2, 20);

        int result = gameRuleEngine.winnerStateUpdater(mockGameState);

        assertEquals(4, result);
    }

    @Test
    void winnerStateUpdater_shouldReturnPlayerWon_whenPlayerHasHigherSum() {
        setupMocks(2, 21, 5, 19);

        int result = gameRuleEngine.winnerStateUpdater(mockGameState);

        assertEquals(6, result);
    }

    @Test
    void winnerStateUpdater_shouldReturnDealerWon_whenDealerHasHigherSum() {
        setupMocks(2, 18, 3, 20);

        int result = gameRuleEngine.winnerStateUpdater(mockGameState);

        assertEquals(7, result, "A Dealer nagyobb összege esetén DEALER_WON (7) az elvárt.");
    }

    @Test
    void canSplit_shouldReturnFalse_whenHandIsNull() {
        assertFalse(gameRuleEngine.canSplit(null),
            "Null bemenet esetén nem osztható.");
    }

    @Test
    void canSplit_shouldReturnFalse_whenHandSizeIsNotTwo() {
        List<Card> singleCardHand = List.of(testCardAce);
        assertFalse(gameRuleEngine.canSplit(singleCardHand));

        List<Card> threeCardHand = List.of(testCardAce, testCardTen, mockDrawnCard);
        assertFalse(gameRuleEngine.canSplit(threeCardHand));
    }

    @Test
    void canSplit_shouldReturnTrue_whenCardsHaveSameRank() {
        List<Card> twoAces = List.of(testCardAce, testCardAce);

        Card mockCardQueen = mockTenValueCard(Rank.QUEEN);
        List<Card> twoQueens = List.of(mockCardQueen, mockCardQueen);

        assertTrue(gameRuleEngine.canSplit(twoAces));
        assertTrue(gameRuleEngine.canSplit(twoQueens));
    }

    @Test
    void canSplit_shouldReturnTrue_whenBothCardsAreTenValueButDifferentRank() {
        Card mockCardKing = mockTenValueCard(Rank.KING);
        Card mockCardJack = mockTenValueCard(Rank.JACK);

        List<Card> tenValueCards = List.of(mockCardKing, mockCardJack);

        assertTrue(gameRuleEngine.canSplit(tenValueCards));
    }

    @Test
    void canSplit_shouldReturnFalse_whenCardsAreDifferentRankAndValue() {
        List<Card> aceAndTen = List.of(testCardAce, testCardTen);

        assertFalse(gameRuleEngine.canSplit(aceAndTen));

        Card mockCardSix = mockTenValueCard(Rank.SIX);
        when(mockCardSix.rank().getBaseValue()).thenReturn(6);
        List<Card> tenAndSix = List.of(testCardTen, mockCardSix);

        assertFalse(gameRuleEngine.canSplit(tenAndSix));
    }
}
