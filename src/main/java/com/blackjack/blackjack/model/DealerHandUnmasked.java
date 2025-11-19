package com.blackjack.blackjack.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

@Data
public class DealerHandUnmasked implements DealerView, Serializable {
    private List<Card> hand;
    private int sum;
    private HandState handStatus;
    private WinnerState natural21Status;

    public DealerHandUnmasked() {
    }

    public DealerHandUnmasked(List<Card> hand, int sum, HandState handStatus, WinnerState natural21Status) {
        this.hand = hand;
        this.sum = sum;
        this.handStatus = handStatus;
        this.natural21Status = natural21Status;
    }

    public static DealerHandUnmasked createEmptyHand() {
        DealerHandUnmasked unMaskedHand = new DealerHandUnmasked();
        unMaskedHand.setHand(Collections.emptyList());
        unMaskedHand.setSum(0);
        unMaskedHand.setHandStatus(HandState.NONE);
        unMaskedHand.setNatural21Status(WinnerState.NONE);
        return unMaskedHand;
    }

    @Override
    public List<Card> getHand() {
        return List.of();
    }

    @Override
    public int getSum() {
        return 0;
    }

    @Override
    public WinnerState getNatural21Status() {
        return null;
    }
}
