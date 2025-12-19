package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GameRuleEngine {
    public int initNatural21State(GameState gameState) {
        PlayerHand playerHand = gameState.getPlayer();
        DealerHandUnmasked dealerHand = gameState.getDealerUnmasked();

        assert playerHand != null;
        int playerSum = playerHand.sum();
        assert dealerHand != null;
        int dealerSum = dealerHand.sum();

        boolean playerNatural = (playerHand.hand().size() == 2) && (playerSum == 21);
        boolean dealerNatural = (dealerHand.hand().size() == 2) && (dealerSum == 21);

        WinnerState naturalState;

        if (playerNatural && dealerNatural) {
            naturalState = WinnerState.BLACKJACK_PUSH;
        } else if (playerNatural) {
            naturalState = WinnerState.BLACKJACK_PLAYER_WON;
        } else if (dealerNatural) {
            naturalState = WinnerState.BLACKJACK_DEALER_WON;
        } else {
            naturalState = WinnerState.NONE;
        }

        return naturalState.getValue();
    }

    public int winnerStateUpdater(PlayerHand playerHand, DealerHandUnmasked dealerHand) {
        if (playerHand == null || dealerHand == null) {
            return WinnerState.NONE.getValue();
        }
        int playerSum = playerHand.sum();
        int dealerSum = dealerHand.sum();
        WinnerState winnerState;

        if (playerSum > 21) {
            winnerState = WinnerState.PLAYER_LOST;
        } else if (dealerSum > 21) {
            winnerState = WinnerState.PLAYER_WON;
        } else if (playerSum == dealerSum) {
            winnerState = WinnerState.PUSH;
        } else if (playerSum > dealerSum) {
            winnerState = WinnerState.PLAYER_WON;
        } else {
            winnerState = WinnerState.DEALER_WON;
        }

        return winnerState.getValue();
    }

    public boolean canSplit(List<Card> hand) {
        if (hand == null || hand.size() != 2) {
            return false;
        }

        Rank rank1 = hand.get(0).rank();
        Rank rank2 = hand.get(1).rank();

        boolean sameRank = rank1 == rank2;
        boolean bothAreTenValue = rank1.getBaseValue() == 10 && rank2.getBaseValue() == 10;

        return sameRank || bothAreTenValue;
    }
}
