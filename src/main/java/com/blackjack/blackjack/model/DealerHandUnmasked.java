package com.blackjack.blackjack.model;

import com.blackjack.blackjack.service.HandStateUpdater;
import com.blackjack.blackjack.service.HandValueCalculator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DealerHandUnmasked implements Hand, Serializable {
    private List<Card> hand;
    private HandState handState;
    private WinnerState natural21;

    public DealerHandUnmasked() {
    }

    public DealerHandUnmasked(List<Card> hand, HandState handState, WinnerState natural21) {
        this.hand = hand;
        this.handState = handState;
        this.natural21 = natural21;
    }

    public static DealerHandUnmasked createEmptyHand() {
        DealerHandUnmasked unMaskedHand = new DealerHandUnmasked();
        unMaskedHand.setHand(new ArrayList<>());
        unMaskedHand.setHandState(HandState.NONE);
        unMaskedHand.setNatural21(WinnerState.NONE);
        return unMaskedHand;
    }

    public int getSum() {
        return HandValueCalculator.calculateSum(this.hand);
    }

    public int getHandState() {
        HandState state = HandStateUpdater.updateHandState(this.getSum(), this.hand.size());

        return state.getValue();
    }
}
