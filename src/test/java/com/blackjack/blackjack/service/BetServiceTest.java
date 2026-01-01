package com.blackjack.blackjack.service;

import com.blackjack.blackjack.common.GameConstants;
import com.blackjack.blackjack.dto.bet.GameStateBet;
import com.blackjack.blackjack.model.GameState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class BetServiceTest {

    @InjectMocks
    private BetService betService;

    @Test
    @DisplayName("PlaceBet: Hiba dobása, ha a kör már aktív")
    void testPlaceBet_ShouldThrowException_WhenRoundIsActive() {
        // GIVEN
        GameState activeState = GameState.builder()
            .isRoundActive(true)
            .build();

        // WHEN & THEN
        assertThrows(ResponseStatusException.class, () -> betService.placeBet(activeState, 100));
    }

    @Test
    @DisplayName("RetakeBet: Hiba dobása, ha a kör már aktív")
    void testRetakeBet_ShouldThrowException_WhenRoundIsActive() {
        // GIVEN
        GameState activeState = GameState.builder()
            .isRoundActive(true)
            .betList(List.of(100))
            .build();

        // WHEN & THEN
        assertThrows(ResponseStatusException.class, () -> betService.retakeBet(activeState));
    }

    @Test
    @DisplayName("Tétrakás halmozódása: 50 + 100 és pakli inicializálás")
    void testPlaceBet_ShouldAccumulateBetsAndHistory() {
        // 1. GIVEN - Kezdőállapot: minden üres, kör nem aktív
        GameState initialState = GameState.builder()
            .bet(0)
            .betList(new ArrayList<>())
            .isRoundActive(false)
            .build();

        // 2. WHEN - Első tét (50)
        GameStateBet dto1 = betService.placeBet(initialState, 50);

        // 3. THEN - Rekord mezők elérése: dto1.bet(), dto1.betList()
        assertEquals(50, dto1.bet());
        assertEquals(List.of(50), dto1.betList());
        assertEquals(GameConstants.INITIAL_DECK_LENGTH, dto1.deckLen());

        // 4. GIVEN - Állapot frissítése az immutabilitás jegyében
        GameState intermediateState = initialState.toBuilder()
            .bet(dto1.bet())
            .betList(dto1.betList())
            .build();

        // 5. WHEN - Második tét (100)
        GameStateBet dto2 = betService.placeBet(intermediateState, 100);

        // 6. THEN
        assertEquals(150, dto2.bet());
        assertEquals(List.of(50, 100), dto2.betList());
        assertEquals(GameConstants.INITIAL_DECK_LENGTH, dto2.deckLen());
    }

    @Test
    @DisplayName("Tét visszavétele: utolsó elem törlése a listából")
    void testRetakeBet_ShouldRemoveLastChipAndReturnAmount() {
        // 1. GIVEN - 150-es kezdőtét (50+100), kör nem aktív
        GameState initialState = GameState.builder()
            .bet(150)
            .betList(new ArrayList<>(List.of(50, 100)))
            .isRoundActive(false)
            .build();

        // 2. WHEN - Első visszavétel (a 100-as jön le)
        BetService.RetakeResult result1 = betService.retakeBet(initialState);
        GameStateBet dto1 = result1.newState();

        // 3. THEN
        assertEquals(100, result1.amountReturned());
        assertEquals(50, dto1.bet());
        assertEquals(List.of(50), dto1.betList());

        // 4. GIVEN - Állapot frissítése a következő lépéshez
        GameState intermediateState = initialState.toBuilder()
            .bet(dto1.bet())
            .betList(dto1.betList())
            .build();

        // 5. WHEN - Második visszavétel (az 50-es jön le)
        BetService.RetakeResult result2 = betService.retakeBet(intermediateState);
        GameStateBet dto2 = result2.newState();

        // 6. THEN
        assertEquals(50, result2.amountReturned());
        assertEquals(0, dto2.bet());
        assertTrue(dto2.betList().isEmpty());
        assertEquals(GameConstants.INITIAL_DECK_LENGTH, dto2.deckLen());
    }
}
