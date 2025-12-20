package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameRuleEngineTest {

    private final GameRuleEngine gameRuleEngine = new GameRuleEngine();

    // Segédmetódusok a kód ismétlés elkerülésére
    private Card createCard(Rank rank) {
        return new Card(Suit.HEARTS, rank);
    }

    private GameState createGameState(int pSum, int pSize, int dSum, int dSize) {
        // Készítünk kártyalistákat a megfelelő méretben (a tartalom mindegy, csak a size() számít)
        List<Card> pCards = new ArrayList<>();
        for (int i = 0; i < pSize; i++) pCards.add(new Card(Suit.HEARTS, Rank.TWO));

        List<Card> dCards = new ArrayList<>();
        for (int i = 0; i < dSize; i++) dCards.add(new Card(Suit.SPADES, Rank.TWO));

        return GameState.builder()
            .player(PlayerHand.builder()
                .sum(pSum)
                .hand(pCards)
                .build())
            .dealerUnmasked(DealerHandUnmasked.builder()
                .sum(dSum)
                .hand(dCards)
                .build())
            .betList(List.of())
            .isRoundActive(true)
            .build();
    }

    // --- 1. initNatural21State Tesztek ---

    @Test
    @DisplayName("Döntetlen, ha mindkettőnek Natural 21-e van")
    void initNatural21State_Push() {
        GameState state = createGameState(21, 2, 21, 2);
        assertEquals(WinnerState.BLACKJACK_PUSH.getValue(), gameRuleEngine.initNatural21State(state));
    }

    @Test
    @DisplayName("Játékos nyer, ha csak neki van Natural 21-e")
    void initNatural21State_PlayerWon() {
        GameState state = createGameState(21, 2, 18, 2);
        assertEquals(WinnerState.BLACKJACK_PLAYER_WON.getValue(), gameRuleEngine.initNatural21State(state));
    }

    @Test
    @DisplayName("Nincs Natural Blackjack, ha 3 lapból van meg a 21")
    void initNatural21State_NoneWithThreeCards() {
        GameState state = createGameState(21, 3, 21, 3);
        assertEquals(WinnerState.NONE.getValue(), gameRuleEngine.initNatural21State(state));
    }

    // --- 2. winnerStateUpdater Tesztek ---

    @Test
    @DisplayName("Játékos veszít, ha besokall (Bust)")
    void winnerStateUpdater_PlayerBust() {
        GameState state = createGameState(22, 3, 18, 2);
        assertEquals(WinnerState.PLAYER_LOST.getValue(), gameRuleEngine.winnerStateUpdater(state.getPlayer(), state.getDealerUnmasked()));
    }

    @Test
    @DisplayName("Játékos nyer, ha az osztó besokall")
    void winnerStateUpdater_DealerBust() {
        GameState state = createGameState(19, 2, 22, 3);
        assertEquals(WinnerState.PLAYER_WON.getValue(), gameRuleEngine.winnerStateUpdater(state.getPlayer(), state.getDealerUnmasked()));
    }

    @Test
    @DisplayName("Döntetlen (Push), ha az összegek egyenlőek")
    void winnerStateUpdater_Push() {
        GameState state = createGameState(20, 2, 20, 3);
        assertEquals(WinnerState.PUSH.getValue(), gameRuleEngine.winnerStateUpdater(state.getPlayer(), state.getDealerUnmasked()));
    }

    // --- 3. canSplit Tesztek ---

    @Test
    @DisplayName("Splitelhető, ha azonos a Rank")
    void canSplit_SameRank() {
        List<Card> hand = List.of(createCard(Rank.EIGHT), createCard(Rank.EIGHT));
        assertTrue(gameRuleEngine.canSplit(hand));
    }

    @Test
    @DisplayName("Splitelhető, ha két különböző 10-es értékű lap (pl. K és J)")
    void canSplit_DifferentTenValues() {
        List<Card> hand = List.of(createCard(Rank.KING), createCard(Rank.JACK));
        assertTrue(gameRuleEngine.canSplit(hand));
    }

    @Test
    @DisplayName("NEM splitelhető, ha eltérő értékek")
    void canSplit_DifferentValues() {
        List<Card> hand = List.of(createCard(Rank.TEN), createCard(Rank.SIX));
        assertFalse(gameRuleEngine.canSplit(hand));
    }

    @Test
    @DisplayName("NEM splitelhető, ha több vagy kevesebb mint 2 lap")
    void canSplit_InvalidSize() {
        assertFalse(gameRuleEngine.canSplit(List.of(createCard(Rank.ACE))));
        assertFalse(gameRuleEngine.canSplit(null));
    }
}
