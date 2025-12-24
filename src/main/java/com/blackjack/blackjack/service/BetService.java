package com.blackjack.blackjack.service;

import com.blackjack.blackjack.dto.bet.GameStateBet;
import com.blackjack.blackjack.model.GameState;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.blackjack.blackjack.common.GameConstants.INITIAL_DECK_LENGTH;
import static com.blackjack.blackjack.common.GameConstants.MINIMUM_BET;

@Service
public class BetService {
    public GameStateBet placeBet(GameState oldState, int betAmount) {
        validateBettingPeriod(oldState);

        if (betAmount < MINIMUM_BET) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BET_TOO_LOW");
        }

        List<Integer> newBetList = new ArrayList<>(oldState.getBetList() != null ? oldState.getBetList() : List.of());
        newBetList.add(betAmount);

        int newTotalBet = oldState.getBet() + betAmount;

        int currentDeckLen = oldState.getDeckLen();
        int deckLenToReturn = (currentDeckLen == 0) ? INITIAL_DECK_LENGTH : currentDeckLen;

        return GameStateBet.builder()
            .bet(newTotalBet)
            .betList(Collections.unmodifiableList(newBetList))
            .deckLen(deckLenToReturn)
            .build();
    }

    public RetakeResult retakeBet(GameState oldState) {
        validateBettingPeriod(oldState);

        if (oldState.getBetList() == null || oldState.getBetList().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NO_BET_TO_RETAKE");
        }

        List<Integer> updatedBetList = new ArrayList<>(oldState.getBetList());
        int amountToReturn = updatedBetList.removeLast();
        int newTotalBet = oldState.getBet() - amountToReturn;

        int currentDeckLen = oldState.getDeckLen();
        int deckLenToReturn = (currentDeckLen == 0) ? INITIAL_DECK_LENGTH : currentDeckLen;

        GameStateBet updatedBetInfo = GameStateBet.builder()
            .bet(newTotalBet)
            .betList(Collections.unmodifiableList(updatedBetList))
            .deckLen(deckLenToReturn)
            .build();

        return new RetakeResult(updatedBetInfo, amountToReturn);
    }

    private void validateBettingPeriod(GameState state) {
        if (state.isRoundActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CANNOT_MODIFY_BET_DURING_ROUND");
        }
    }

    public record RetakeResult(GameStateBet newState, int amountReturned) {
    }

}
