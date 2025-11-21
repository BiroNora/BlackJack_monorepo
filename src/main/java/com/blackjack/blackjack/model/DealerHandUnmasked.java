package com.blackjack.blackjack.model;

import com.blackjack.blackjack.service.HandStateUpdater;
import com.blackjack.blackjack.service.HandValueCalculator;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class DealerHandUnmasked implements Hand, Serializable {
    private List<Card> hand;
    private HandState handState;
    private WinnerState natural21Status;

    public DealerHandUnmasked() {
    }

    public DealerHandUnmasked(List<Card> hand, HandState handState, WinnerState natural21Status) {
        this.hand = hand;
        this.handState = handState;
        this.natural21Status = natural21Status;
    }

    public static DealerHandUnmasked createEmptyHand() {
        DealerHandUnmasked unMaskedHand = new DealerHandUnmasked();
        unMaskedHand.setHand(new ArrayList<>());
        unMaskedHand.setHandState(HandState.NONE);
        unMaskedHand.setNatural21Status(WinnerState.NONE);
        return unMaskedHand;
    }

    public int getSum() {
        return HandValueCalculator.calculateSum(this.hand);
    }

    public HandState getHandState() {
        return HandStateUpdater.updateHandState(this.getSum(), this.hand.size());
    }
}
