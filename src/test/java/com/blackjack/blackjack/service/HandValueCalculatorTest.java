package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.Card;
import com.blackjack.blackjack.model.Rank;
import com.blackjack.blackjack.model.Suit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HandValueCalculatorTest {
    private HandValueCalculator handValueCalculator;

    @BeforeEach
    void setUp() {
        handValueCalculator = new HandValueCalculator();
    }

    /**
     * Test case 1: Standard sum without Aces (should be a simple addition).
     * Cards: 8, 9, 2
     * Expected Sum: 19
     */
    @Test
    void testStandardSumWithoutAces() {
        List<Card> hand = Arrays.asList(
            new Card(Suit.HEARTS, Rank.EIGHT),  // 8
            new Card(Suit.SPADES, Rank.NINE),   // 9
            new Card(Suit.DIAMONDS, Rank.TWO)   // 2
        );

        int sum = handValueCalculator.calculateSum(hand);
        assertEquals(19, sum);
    }

    /**
     * Test case 2: Blackjack (Natural 21) - Ace valued as 11.
     * Cards: Ace, King
     * Expected Sum: 21
     */
    @Test
    void testNaturalBlackjack() {
        List<Card> hand = Arrays.asList(
            new Card(Suit.HEARTS, Rank.ACE),   // 11
            new Card(Suit.CLUBS, Rank.KING)    // 10
        );

        int sum = handValueCalculator.calculateSum(hand);
        assertEquals(21, sum);
    }

    /**
     * Test case 3: Ace valued as 11, resulting in less than 21.
     * Cards: Ace, 5, 2
     * Expected Sum: 18 (11 + 5 + 2)
     */
    @Test
    void testAceValuedAsEleven() {
        List<Card> hand = Arrays.asList(
            new Card(Suit.SPADES, Rank.ACE),   // 11
            new Card(Suit.DIAMONDS, Rank.FIVE), // 5
            new Card(Suit.CLUBS, Rank.TWO)     // 2
        );

        int sum = handValueCalculator.calculateSum(hand);
        assertEquals(18, sum);
    }

    /**
     * Test case 4: Ace valued as 1 to prevent BUST (sum > 21).
     * Cards: Ace, Queen, 9
     * Expected Sum: 20 (1 + 10 + 9) -> 11 + 10 + 9 = 30 (BUST), so Ace is 1.
     */
    @Test
    void testAceValuedAsOneToAvoidBust() {
        List<Card> hand = Arrays.asList(
            new Card(Suit.CLUBS, Rank.ACE),    // 1
            new Card(Suit.HEARTS, Rank.QUEEN),  // 10
            new Card(Suit.SPADES, Rank.NINE)    // 9
        );

        int sum = handValueCalculator.calculateSum(hand);
        assertEquals(20, sum);
    }

    /**
     * Test case 5: Multiple Aces (complex calculation).
     * Cards: Ace, Ace, 8, 2
     * Expected Sum: 22 -> (11 + 11 + 8 + 2 = 32 (BUST)), so (11 + 1 + 8 + 2 = 22 (BUST)), so (1 + 1 + 8 + 2 = 12)
     * No, wait: 11 + 1 + 8 + 2 = 22. If we value both as 1, sum is 12.
     * The correct logic should be: 11 (first Ace) + 1 (second Ace) + 8 + 2 = 22 (BUST).
     * The logic must be: (1 + 1 + 8 + 2 = 12).
     * * Let's check a sum that is NOT a bust: Ace, Ace, 5
     * Expected Sum: 17 (11 + 1 + 5 = 17)
     */
    @Test
    void testMultipleAcesComplexSum() {
        List<Card> hand = Arrays.asList(
            new Card(Suit.CLUBS, Rank.ACE),     // 11
            new Card(Suit.DIAMONDS, Rank.ACE),  // 1
            new Card(Suit.HEARTS, Rank.FIVE)    // 5
        );

        int sum = handValueCalculator.calculateSum(hand);
        assertEquals(17, sum);
    }

    /**
     * Test case 6: The BUST case with Aces.
     * Cards: Ace, Ace, Ace, 9
     * Expected Sum: 12 (1+1+1+9) -> 11+1+1+9 = 22 (BUST). All must be 1.
     */
    @Test
    void testMultipleAcesResultingInBust() {
        List<Card> hand = Arrays.asList(
            new Card(Suit.CLUBS, Rank.ACE),    // 1
            new Card(Suit.DIAMONDS, Rank.ACE), // 1
            new Card(Suit.HEARTS, Rank.ACE),   // 1
            new Card(Suit.SPADES, Rank.NINE)   // 9
        );

        int sum = handValueCalculator.calculateSum(hand);
        assertEquals(12, sum);
    }

    @Test
    @DisplayName("Üres kéz értéke nulla")
    void testEmptyHandReturnsZero() {
        assertEquals(0, handValueCalculator.calculateSum(List.of()));
        assertEquals(0, handValueCalculator.calculateSum(null));
    }
}
