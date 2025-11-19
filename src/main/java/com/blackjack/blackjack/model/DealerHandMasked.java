package com.blackjack.blackjack.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

@Data
public class DealerHandMasked implements DealerView, Serializable {
    private List<Card> hand;
    private int sum;
    private boolean canInsure;
    private WinnerState natural21State;

    public DealerHandMasked() {
    }

    public DealerHandMasked(List<Card> hand, int sum, boolean canInsure, WinnerState natural21State) {
        this.hand = hand;
        this.sum = sum;
        this.canInsure = canInsure;
        this.natural21State = natural21State;
    }

    public static DealerHandMasked createEmptyHand() {
        DealerHandMasked maskedHand = new DealerHandMasked();
        maskedHand.setHand(Collections.emptyList());
        maskedHand.setSum(0);
        maskedHand.setCanInsure(false);
        maskedHand.setNatural21State(WinnerState.NONE);
        return maskedHand;
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
